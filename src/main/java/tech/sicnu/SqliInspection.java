package tech.sicnu;

import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Query;
import com.sun.javafx.fxml.expression.LiteralExpression;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jdt.internal.compiler.ast.ReferenceExpression;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class SqliInspection extends AbstractBaseJavaLocalInspectionTool {
    private final String STR = "str";
    private final String IF_LITERAL = "ifLiteral";
    private final String CUSTOM_STR  = "CUSTOM_STR";

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        return new JavaElementVisitor() {
            private final String DESCRIPTION_TEMPLATE = "AxinSAST: SQL Injection Found!!!";

            @Override
            public void visitPolyadicExpression(PsiPolyadicExpression expression) {
                super.visitPolyadicExpression(expression);
                Map<String, String> result = getPolyadicExpValue(expression);
                System.out.println(result.get(STR));
                if(result.size() != 0 && result.get(IF_LITERAL).equals("false") && isSql(result.get(STR))){
                    holder.registerProblem(expression, DESCRIPTION_TEMPLATE);
                }
            }

            @Override
            public void visitAssignmentExpression(PsiAssignmentExpression expression) {
                super.visitAssignmentExpression(expression);
                IElementType opToken = expression.getOperationTokenType();
                if(opToken.equals(JavaTokenType.PLUSEQ)){
                    PsiExpression exp = expression.getLExpression();
                    Map<String,String> lResult = resolveReference((PsiReferenceExpression) exp, exp.getTextOffset());
                    Map<String, String> rResult = getVariableValue(expression);
                    if((lResult.get(IF_LITERAL).equals("false") || rResult.get(IF_LITERAL).equals("false")) && isSql(lResult.get(STR)+rResult.get(STR))){
                        holder.registerProblem(expression, DESCRIPTION_TEMPLATE);
                    }
                }
            }

            @Override
            public void visitMethodCallExpression(PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                PsiReferenceExpression methodRef = expression.getMethodExpression();
                String qualifiedName = methodRef.getQualifiedName();

                if(qualifiedName.equals("String.format")||qualifiedName.equals("MessageFormat.format")){
                    PsiExpressionList argList = expression.getArgumentList();
                    PsiExpression[] args = argList.getExpressions();
                    PsiReferenceExpression arg0 = ObjectUtils.tryCast(args[0], PsiReferenceExpression.class);
                    List<PsiExpression> argReal;
                    argReal = Arrays.stream(args).collect(Collectors.toList());
                    if(arg0 != null && arg0.getCanonicalText().contains("Locale")){
                        argReal.remove(0);
                    }
                    PsiExpression argPattern = argReal.get(0);
                    // ????????????????????????expression??????????????????
                    String text  = "";
                    if(argPattern instanceof PsiLiteralExpression){
                        text = StringUtils.strip(ObjectUtils.tryCast(argPattern, PsiLiteralExpression.class).getText(), "\"");
                    }else if(argPattern instanceof PsiReferenceExpression){
                        Map<String, String> result = resolveReference((PsiReferenceExpression) argPattern, argPattern.getTextOffset());
                        if(result.size() != 0){
                            text = result.get(STR);
                        }
                    }
                    if(isSql(text)){
                        argReal.remove(0);
                        for(PsiExpression arg:argReal){
                            // ????????????????????????????????????????????????????????????????????????
                            // String.format("select * from table where id=%s", talbe+"123");
                            if(arg instanceof PsiReferenceExpression){
                                Map<String, String> result = resolveReference((PsiReferenceExpression) arg, arg.getTextOffset());
                                if(result.get(IF_LITERAL).equals("false")){
                                    holder.registerProblem(expression, DESCRIPTION_TEMPLATE);
                                }
                            }else if(arg instanceof PsiMethodCallExpression){
                                holder.registerProblem(expression, DESCRIPTION_TEMPLATE);
                            }
                        }
                    }
                }
            }
        };
    }

    @Override
    public @Nullable JComponent createOptionsPanel() {
        return super.createOptionsPanel();
    }

    /**
     * ?????????????????????
     * @param ployExp ?????????????????????????????????
     * @return Map<String, String>,??????key:str??????ifLiteral,str?????????????????????ifLiteral?????????????????????????????????,????????????????????????size???0???map
     */
    public Map<String,String> getPolyadicExpValue(PsiPolyadicExpression ployExp){
        String ifLiteral = "true"; // ??????????????????????????????????????????????????????
        List<String> strOps = new ArrayList<>(); // ????????????????????????????????????????????????????????????
        Map<String, String> result = new HashMap<>(); // ???????????????Map
        IElementType opSign = ployExp.getOperationTokenType();
        if(opSign == JavaTokenType.PLUS){
            PsiExpression[] ops = ployExp.getOperands();
            for(PsiExpression op : ops){
                // LiteralString??????
                if(op instanceof PsiLiteralExpression){
                    PsiLiteralExpression opLiteral = ObjectUtils.tryCast(op, PsiLiteralExpression.class);
                    PsiType opType = opLiteral.getType();
                    if(!opType.getCanonicalText().equals("java.lang.String")){
                        break;
                    }

                    String strOp = opLiteral.getText();
                    strOps.add(StringUtils.strip(strOp, "\""));
                }

                if (op instanceof PsiReferenceExpression){
                    if(!op.getType().getCanonicalText().equals("java.lang.String")){
                        break;
                    }
                    Map<String, String> res = resolveReference((PsiReferenceExpression) op, op.getTextOffset());
                    strOps.add(res.get(STR));
                    ifLiteral = res.get(IF_LITERAL);
                }

                if(op instanceof  PsiMethodCallExpression){
                    PsiMethodCallExpression opMethodCall = ObjectUtils.tryCast(op, PsiMethodCallExpression.class);
                    PsiReferenceExpression method = opMethodCall.getMethodExpression();
                    PsiElement firstChildEle = method.resolve();
                    PsiMethod psiMehtod = ObjectUtils.tryCast(firstChildEle, PsiMethod.class);
                    if(Objects.requireNonNull(psiMehtod.getReturnType()).getCanonicalText().equals("java.lang.String")){
                        strOps.add(CUSTOM_STR);
                        ifLiteral = "false";
                    }
                }
            }
            result.put(STR, StringUtils.join(strOps.toArray()));
            result.put(IF_LITERAL, ifLiteral);
        }
        // ?????????????????????result????????????
        return result;
    }

    /**
     * ????????????reference??????
     * @param reference ????????????????????????reference
     * @param offset ???reference?????????????????????textOffset
     * @return
     */
    public Map<String, String> resolveReference(PsiReferenceExpression reference, int offset){
        // ???????????????????????????????????????
        PsiElement element = reference.resolve();
        Map<String, String> res = new HashMap<>();
        // ????????????
        if(element instanceof PsiLocalVariable){
            PsiLocalVariable eleLocal = ObjectUtils.tryCast(element, PsiLocalVariable.class);
            PsiElement parentMethod = PsiTreeUtil.getParentOfType(eleLocal, PsiMethod.class);
            List<PsiReference> allReferences = new ArrayList<>(ReferencesSearch.search(eleLocal, new LocalSearchScope(new PsiElement[]{parentMethod})).findAll());
            // ??????????????????
            if(allReferences.size() == 1){
                return getVariableValue(eleLocal);
            }
            // ?????????????????????????????????????????????????????????????????????????????????????????????
            // ?????????????????????????????????literal???????????????????????????????????????????????????if
            allReferences = allReferences.stream().filter(item -> item.getElement().getTextOffset() < offset).collect(Collectors.toList());
            for(PsiReference ref:allReferences){
                PsiElement ele = ref.getElement();
                PsiElement parent = ele.getParent();
                // ????????????????????????????????????????????????????????????????????????????????????
                if(parent instanceof PsiAssignmentExpression){
                    res = getVariableValue(parent);
                    if(res.get(IF_LITERAL).equals("false")){
                        return res;
                    }
                }
            }
            // ????????????????????????????????????????????????????????????????????????
            return getVariableValue(eleLocal);
        }

        // ????????????,???????????????????????????????????????????????????
        if(element instanceof PsiParameter){
            res.put(IF_LITERAL, "false");
            res.put(STR, CUSTOM_STR);
            return res;
        }

        // ??????????????????????????????????????????????????????????????????????????????????????????
        if(element instanceof PsiField){
            res.put(IF_LITERAL, "false");
            res.put(STR, CUSTOM_STR);
            return res;
        }
        return res;
    }

    /**
     * ??????????????????????????????????????????????????????????????????
     * @param expression PsiAssignmentExpression??????PsiLocalVariable
     * @return
     */
    public Map<String, String> getVariableValue(PsiElement expression){
        String ifLiteral = "true";
        List<String> strs = new ArrayList<>();
        Map<String, String> res = new HashMap<>();
        boolean isFirst = true;
        PsiElement[] children = expression.getChildren();
        for(PsiElement child:children){
            // ?????????expression????????????expression
            if(child instanceof PsiExpression){
                if(ObjectUtils.tryCast(expression, PsiAssignmentExpression.class) != null && isFirst){
                    isFirst = false;
                    continue;
                }
                PsiLiteralExpression literalExpression = ObjectUtils.tryCast(child, PsiLiteralExpression.class);
                if(literalExpression != null){
                    strs.add(StringUtils.strip(literalExpression.getText(), "\""));
                    continue;
                }
                PsiReferenceExpression referenceExpression = ObjectUtils.tryCast(child, PsiReferenceExpression.class);
                if(referenceExpression != null){
                    Map<String,String> resTemp = resolveReference(referenceExpression, child.getTextOffset());
                    ifLiteral = resTemp.get(IF_LITERAL);
                    strs.add(resTemp.get(STR));
                    continue;
                }

                PsiMethodCallExpression methodCallExpression = ObjectUtils.tryCast(child, PsiMethodCallExpression.class);
                if(methodCallExpression != null){
                    ifLiteral = "false";
                    strs.add(CUSTOM_STR);
                }

                PsiPolyadicExpression polyadicExpression = ObjectUtils.tryCast(child, PsiPolyadicExpression.class);
                if(polyadicExpression != null){
                    return getPolyadicExpValue(polyadicExpression);
                }
            }
        }
        res.put(STR, StringUtils.join(strs.toArray()));
        res.put(IF_LITERAL, ifLiteral);
        return res;
    }

    public boolean isSql(String str){
        String sqlPattern = "select +.+? +from|insert +into +.+? +values|update +.+? +set +.+?=.+?|delete +from +.+?where";
        Pattern pattern = Pattern.compile(sqlPattern, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(str);
        return matcher.find();
    }
}
