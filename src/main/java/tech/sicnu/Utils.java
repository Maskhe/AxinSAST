package tech.sicnu;

import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import org.apache.commons.lang.StringUtils;

import java.util.*;
import java.util.stream.Collectors;


public class Utils {

    /**
     * 获取多项式的值
     * @param ployExp 想要获取值的目标多项式
     * @return Map<String, String>,两个key:str以及ifLiteral,str是多项式的值，ifLiteral代表这个是否是纯字符串,也有可能返回一个size为0的map
     */
    public static Map<String,String> getPolyadicExpValue(PsiPolyadicExpression ployExp){
        String ifLiteral = "true"; // 标志位，标志当前多项式是否为纯字符串
        List<String> strOps = new ArrayList<>(); // 存放多项式的各个操作数的值（字符串格式）
        Map<String, String> result = new HashMap<>(); // 存放结果的Map
        IElementType opSign = ployExp.getOperationTokenType();
        if(opSign == JavaTokenType.PLUS){
            PsiExpression[] ops = ployExp.getOperands();
            for(PsiExpression op : ops){
                // LiteralString判断
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
                    strOps.add(res.get(Config.STR));
                    ifLiteral = res.get(Config.IF_LITERAL);
                }

                if(op instanceof  PsiMethodCallExpression){
                    PsiMethodCallExpression opMethodCall = ObjectUtils.tryCast(op, PsiMethodCallExpression.class);
                    PsiReferenceExpression method = opMethodCall.getMethodExpression();
                    PsiElement firstChildEle = method.resolve();
                    PsiMethod psiMehtod = ObjectUtils.tryCast(firstChildEle, PsiMethod.class);
                    if(Objects.requireNonNull(psiMehtod.getReturnType()).getCanonicalText().equals("java.lang.String")){
                        strOps.add(Config.CUSTOM_STR);
                        ifLiteral = "false";
                    }
                }
            }
            result.put(Config.STR, StringUtils.join(strOps.toArray()));
            result.put(Config.IF_LITERAL, ifLiteral);
        }
        // 在外面记得判断result是否为空
        return result;
    }

    /**
     * 尝试拿到reference的值
     * @param reference 想要获取值的目标reference
     * @param offset 该reference在当前文件中的textOffset
     * @return
     */
    public static Map<String, String> resolveReference(PsiReferenceExpression reference, int offset){
        // 拿到该引用最初被声明的地方
        PsiElement element = reference.resolve();
        Map<String, String> res = new HashMap<>();
        // 本地变量
        if(element instanceof PsiLocalVariable){
            PsiLocalVariable eleLocal = ObjectUtils.tryCast(element, PsiLocalVariable.class);
            PsiElement parentMethod = PsiTreeUtil.getParentOfType(eleLocal, PsiMethod.class);
            List<PsiReference> allReferences = new ArrayList<>(ReferencesSearch.search(eleLocal, new LocalSearchScope(new PsiElement[]{parentMethod})).findAll());
            // 只有一处引用
            if(allReferences.size() == 1){
                return getVariableValue(eleLocal);
            }
            // 拿到当前引用之前的所有引用，然后再去找这些引用中有没有赋值操作
            // 只要有一次赋值操作时非literal的，就认为是可以的，因为我无法处理if
            allReferences = allReferences.stream().filter(item -> item.getElement().getTextOffset() < offset).collect(Collectors.toList());
            for(PsiReference ref:allReferences){
                PsiElement ele = ref.getElement();
                PsiElement parent = ele.getParent();
                // 这里没考虑到是把当前引用赋值给别人的情况，但是好像不影响
                if(parent instanceof PsiAssignmentExpression){
                    res = getVariableValue(parent);
                    if(res.get(Config.IF_LITERAL).equals("false")){
                        return res;
                    }
                }
            }
            // 该本地变量没有被赋值，那就是在声明的时候被赋值了
            return getVariableValue(eleLocal);
        }

        // 函数参数,只要该引用来自参数，都认为是可疑的
        if(element instanceof PsiParameter){
            String aFalse = res.put(Config.IF_LITERAL, "false");
            res.put(Config.STR, Config.CUSTOM_STR);
            return res;
        }

        // 对象的某个字段，只要是来自某个对象的某个字段，都认为是可疑的
        if(element instanceof PsiField){
            res.put(Config.IF_LITERAL, "false");
            res.put(Config.STR, Config.CUSTOM_STR);
            return res;
        }
        return res;
    }

    /**
     * 从赋值语句或者变量声明语句中获得某个变量的值
     * @param expression PsiAssignmentExpression或者PsiLocalVariable
     * @return
     */
    public static Map<String, String> getVariableValue(PsiElement expression){
        String ifLiteral = "true";
        List<String> strs = new ArrayList<>();
        Map<String, String> res = new HashMap<>();
        boolean isFirst = true;
        PsiElement[] children = expression.getChildren();
        for(PsiElement child:children){
            // 拿到该expression下的所有expression
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
                    ifLiteral = resTemp.get(Config.IF_LITERAL);
                    strs.add(resTemp.get(Config.STR));
                    continue;
                }

                PsiMethodCallExpression methodCallExpression = ObjectUtils.tryCast(child, PsiMethodCallExpression.class);
                if(methodCallExpression != null){
                    ifLiteral = "false";
                    strs.add(Config.CUSTOM_STR);
                }

                PsiPolyadicExpression polyadicExpression = ObjectUtils.tryCast(child, PsiPolyadicExpression.class);
                if(polyadicExpression != null){
                    return getPolyadicExpValue(polyadicExpression);
                }
            }
        }
        res.put(Config.STR, StringUtils.join(strs.toArray()));
        res.put(Config.IF_LITERAL, ifLiteral);
        return res;
    }
}
