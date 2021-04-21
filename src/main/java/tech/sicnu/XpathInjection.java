package tech.sicnu;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XpathInjection extends AbstractBaseJavaLocalInspectionTool {
    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
            private final String DESCRIPTION_TEMPLATE = "AxinSAST: XPath Injection Found!!!";
            @Override
            public void visitMethodCallExpression(PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                PsiReferenceExpression referenceExpression = (PsiReferenceExpression) expression.getFirstChild();
                String methodName = referenceExpression.getLastChild().getText();
                if(methodName.equals("newXPath")){
                    PsiElement instance = PsiTreeUtil.getParentOfType(expression, PsiLocalVariable.class, PsiAssignmentExpression.class);
                    // System.out.println(ObjectUtils.tryCast(instance, PsiLocalVariable.class).getNameIdentifier().getText());
                    PsiElement parentMethod = PsiTreeUtil.getParentOfType(instance, PsiMethod.class);
                    // 查找实例在当前method中的所有引用，然后看这些引用是否正确调用了对应的setFeature方法
                    List<PsiReference> allReferences = new ArrayList<>(ReferencesSearch.search(instance, new LocalSearchScope(new PsiElement[]{parentMethod})).findAll());
                    Map<?, ?> res = new HashMap<>();
                    PsiExpression vulPoint = null;
                    for(PsiReference reference:allReferences){
                        PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType((PsiElement) reference, PsiMethodCallExpression.class);
                        // feaure = factory.setXXX(x,x)的格式
                        String method = methodCall.getText();
                        if(method.contains("compile") || method.contains("evaluate")){
                            PsiExpression[] args = methodCall.getArgumentList().getExpressions();
                            if(args.length >= 1){
                                vulPoint = args[0];
                                if(args[0] instanceof PsiReferenceExpression){
                                    res = Utils.resolveReference((PsiReferenceExpression) args[0], args[0].getTextOffset());
                                }

                                if(args[0] instanceof PsiPolyadicExpression){
                                    res = Utils.getPolyadicExpValue((PsiPolyadicExpression) args[0]);
                                }
                            }
                        }
                    }
                    if(res.size() != 0){
                        if(res.get(Config.IF_LITERAL).equals("false")){
                            holder.registerProblem(vulPoint, DESCRIPTION_TEMPLATE);
                        }
                    }
                }
            }
        };
    }
}
