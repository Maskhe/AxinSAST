package tech.sicnu;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;


public class XXEInspection extends AbstractBaseJavaLocalInspectionTool {
    public static List<String> FACTORIES = new ArrayList<>(Arrays.asList("XMLInputFactory.newInstance",
            "DocumentBuilderFactory.newInstance",
            "SAXParserFactory.newInstance",
            "TransformerFactory.newInstance",
            "SchemaFactory.newInstance",
            "SAXTransformerFactory.newInstance",
            "XMLReaderFactory.createXMLReader"));
    protected final List<String> ENTRANCES = new ArrayList<>(Arrays.asList("DOMParser", "SAXReader", "SAXBuilder"));
//    public HashMap<String, Map<?,?>> test = new HashMap<String, Map<String, Map<String, String>>>(){
//        {
//            HashMap<String, String> attr = new HashMap<String, String>();
//            attr.put("http://apache.org/xml/features/disallow-doctype-decl", "false");
//            attr.put("");
//            test.put("XMLInputFactory.newInstance", new )
//        }
//    };
//    public HashMap<String, Integer> dbfSec = new HashMap<String, Integer>(){
//        {
//            dbfSec.put("http://apache.org/xml/features/disallow-doctype-decl", 4);
//            dbfSec.put("http://xml.org/sax/features/external-general-entities", 2);
//            dbfSec.put("http://xml.org/sax/features/external-parameter-entities", 2);
//            dbfSec.put("http://apache.org/xml/features/nonvalidating/load-external-dtd", 2);
//        }
//    };
//
//    public HashMap<String, Integer> xiSec = new HashMap<String, Integer>(){
//        {
//            xiSec.put("XMLInputFactory.SUPPORT_DTD", 4);
//            xiSec.put("javax.xml.stream.isSupportingExternalEntities", 4);
//        }
//    };
//
//    public HashMap<String, Integer> dpSec = new HashMap<String, Integer>(){
//        {
//            dpSec.put("DOMParser.EXPAND_ENTITYREF", 2);
//            dpSec.put("DOMParser.DTD_OBJECT", 2);
//            dpSec.put("DOMParser.ENTITY_EXPANSION_DEPTH", 2);
//        }
//    };
//
//    public HashMap<String, Integer> tfSec = new HashMap<String, Integer>(){
//        {
//            tfSec.put("XMLConstants.ACCESS_EXTERNAL_DTD", 2);
//            tfSec.put("XMLConstants.ACCESS_EXTERNAL_STYLESHEET", 2);
//        }
//    };
//
//    public HashMap<String, Integer> sfSec = new HashMap<String, Integer>(){
//        {
//            sfSec.put("XMLConstants.ACCESS_EXTERNAL_DTD", 2);
//            sfSec.put("XMLConstants.ACCESS_EXTERNAL_SCHEMA", 2);
//        }
//    };
//
//    public HashMap<String, Integer> saxSec = new HashMap<String, Integer>(){
//        {
//            saxSec.put("XMLConstants.ACCESS_EXTERNAL_DTD", 2);
//            saxSec.put("XMLConstants.ACCESS_EXTERNAL_STYLESHEET", 2);
//        }
//    };
//
//    public HashMap<String, Integer> saxrSec = new HashMap<String, Integer>(){
//        {
//            saxrSec.put("http://apache.org/xml/features/disallow-doctype-decl", 2);
//            saxrSec.put("http://xml.org/sax/features/external-general-entities", 2);
//            saxrSec.put("http://xml.org/sax/features/external-parameter-entities", 2);
//        }
//    };
//
//    public HashMap<String, Integer> saxbSec = new HashMap<String, Integer>(){
//        {
//            saxbSec.put("http://apache.org/xml/features/disallow-doctype-decl", 2);
//            saxbSec.put("http://xml.org/sax/features/external-general-entities", 2);
//            saxbSec.put("http://xml.org/sax/features/external-parameter-entities", 2);
//        }
//    };

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
            private final String DESCRIPTION_TEMPLATE = "AxinSAST: XXE Found!!!";
            @Override
            public void visitMethodCallExpression(PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                if(isVul(expression, FACTORIES)){
                    holder.registerProblem(expression, DESCRIPTION_TEMPLATE);
                }
            }

            @Override
            public void visitNewExpression(PsiNewExpression expression) {
                super.visitNewExpression(expression);
                if(isVul(expression, ENTRANCES)){
                    holder.registerProblem(expression, DESCRIPTION_TEMPLATE);
                }
            }
        };
    }

    /*
    * ?????????????????????????????????offset
     */
    protected int getMethodOffset(List<PsiReference> references, String methodName){
        for(PsiReference reference : references){
            PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType((PsiElement) reference, PsiMethodCallExpression.class);
            // feaure = factory.setXXX(x,x)?????????
            String feature = methodCall.getText();
            if(feature.contains(methodName)){
                return methodCall.getTextOffset();
            }
        }
        return 99999999;
    }

    protected boolean isVul(PsiExpression expression, List<String> factories){
        String text = expression.getText();
        for(String factory:factories){
            if(text.contains(factory)){
                // ???????????????????????????newInstance,??????????????????????????????
                PsiElement instance = PsiTreeUtil.getParentOfType(expression, PsiLocalVariable.class, PsiAssignmentExpression.class);
                // System.out.println(ObjectUtils.tryCast(instance, PsiLocalVariable.class).getNameIdentifier().getText());
                PsiElement parentMethod = PsiTreeUtil.getParentOfType(instance, PsiMethod.class);
                // ?????????????????????method????????????????????????????????????????????????????????????????????????setFeature??????
                List<PsiReference> allReferences = new ArrayList<>(ReferencesSearch.search(instance, new LocalSearchScope(new PsiElement[]{parentMethod})).findAll());

                return isXXE(factory, allReferences);
            }
        }
        return false;
    }
    /**
     * ??????????????????????????????????????????????????????????????????
     * @param factory ???????????????????????????XMLInputFactory.newInstance
     * @param allReferences ???????????????????????????????????????
     * @return ????????????????????????xxe
     */
    protected boolean isXXE(String factory, List<PsiReference> allReferences){
        boolean xxe = false;
        String keyMethod;
        String setMethod;
        switch (factory){
            case "XMLInputFactory.newInstance":
                keyMethod = "createXMLStreamReader";
                setMethod = "setProperty";
                break;
            case "DocumentBuilderFactory.newInstance":
                keyMethod = "newDocumentBuilder";
                setMethod = "setFeature";
                break;
            case "SAXParserFactory.newInstance":
                keyMethod = "newSAXParser";
                setMethod = "setFeature";
                break;
            case "TransformerFactory.newInstance":
                keyMethod = "newTransformer";
                setMethod = "setAttribute";
                break;
            case "SchemaFactory.newInstance":
                keyMethod = "newSchema";
                setMethod = "setProperty";
                break;
            case "SAXTransformerFactory.newInstance":
                keyMethod = "newXMLFilter";
                setMethod = "setAttribute";
                break;
            case "XMLReaderFactory.createXMLReader":
                keyMethod = "parse";
                setMethod = "setFeature";
                break;
            case "SAXBuilder":
                keyMethod =  "build";
                setMethod = "setFeature";
                break;
            case "SAXReader":
                keyMethod = "read";
                setMethod = "setFeature";
                break;
                // DOMparser??????????????????????????????????????????????????????????????????
            case "DOMParser":
                keyMethod = "parse";
                setMethod = "setAttribute";
                break;
            default:
                keyMethod = "";
                setMethod = "";
        }
        boolean vulTmp1 = true;
        boolean vulTmp2 = true;
        boolean vulTmp3 = true;
        boolean vulTmp4 = true;
        // ???????????????????????????????????????????????????method????????????createXMLStreamReader?????????????????????????????????
        int createXMLStreamReaderOffset;
        // ??????createXMLStreamReader???offset
        createXMLStreamReaderOffset = getMethodOffset(allReferences, keyMethod);
        for (PsiReference reference : allReferences) {
            PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType((PsiElement) reference, PsiMethodCallExpression.class);
            // feaure = factory.setXXX(x,x)?????????
            String feature = methodCall.getText();
            int offset = methodCall.getTextOffset();
            if (feature.contains(setMethod) && offset<createXMLStreamReaderOffset) {

                PsiExpression[] args = methodCall.getArgumentList().getExpressions();
                if (args.length == 2) {
                    if(factory.equals("XMLInputFactory.newInstance")){
                        if (args[0] instanceof PsiReferenceExpression) {
                            if (args[0].getText().equals("XMLInputFactory.SUPPORT_DTD") && args[1].getText().equals("false")) {
                                vulTmp1 = false;
                            } else {
                                Map<?, ?> res = Utils.resolveReference((PsiReferenceExpression) args[0], args[0].getTextOffset());
                                if (res.get(Config.STR).equals("javax.xml.stream.isSupportingExternalEntities") && args[1].getText().equals("false")) {
                                    System.out.println(res.get(Config.STR));
                                    vulTmp1 = false;
                                }
                            }
                        }

                        if (args[0] instanceof PsiLiteralExpression && args[0].getText().equals("\"javax.xml.stream.isSupportingExternalEntities\"")
                                && args[1].getText().equals("false")) {
                            vulTmp1 = false;
                        }
                    }

                    if(factory.equals("DocumentBuilderFactory.newInstance") ||
                            factory.equals("SAXParserFactory.newInstance") ||
                            factory.equals("XMLReaderFactory.createXMLReader") ||
                            factory.equals("SAXReader") ||
                            factory.equals("SAXBuilder")
                    ){
                        String attrKey = "";
                        if (args[0] instanceof PsiLiteralExpression) {
                            // ?????????,?????????????????????
                            attrKey = StringUtils.strip(args[0].getText(), "\"");
                        }

                        if (args[0] instanceof PsiReferenceExpression) {
                            Map<?, ?> res = Utils.resolveReference((PsiReferenceExpression) args[0], args[0].getTextOffset());
                            attrKey = (String) res.get(Config.STR);
                        }
                        if (attrKey.equals("http://apache.org/xml/features/disallow-doctype-decl") && args[1].getText().equals("true")) {
                            vulTmp1 = false;
                        }

                        if (attrKey.equals("http://xml.org/sax/features/external-general-entities") && args[1].getText().equals("false")) {
                            vulTmp2 = false;
                        }

                        if (attrKey.equals("http://xml.org/sax/features/external-parameter-entities") && args[1].getText().equals("false")) {
                            vulTmp3 = false;
                        }

                        if (attrKey.equals("http://apache.org/xml/features/nonvalidating/load-external-dtd") && args[1].getText().equals("false")) {
                            vulTmp4 = false;
                        }
                    }

                    if(factory.equals("TransformerFactory.newInstance") ||
                            factory.equals("SAXTransformerFactory.newInstance") ||
                            factory.equals("SchemaFactory.newInstance")){
                        if(args[0] instanceof PsiReferenceExpression){
                            if (args[0].getText().equals("XMLConstants.ACCESS_EXTERNAL_DTD") && args[1].getText().equals("\"\"")) {
                                vulTmp1 = false;
                            }
                        }
                    }
                }
            }
        }
        if(factory.equals("XMLInputFactory.newInstance") && vulTmp1){
            xxe = true;
        }

        if((factory.equals("DocumentBuilderFactory.newInstance") || factory.equals("SAXParserFactory.newInstance")
        || factory.equals("XMLReaderFactory.createXMLReader") || factory.equals("SAXReader") ||
                factory.equals("SAXBuilder"))&& vulTmp1 && (vulTmp2|vulTmp3|vulTmp4)){
            xxe = true;
        }

        if((factory.equals("TransformerFactory.newInstance") || factory.equals("SAXTransformerFactory.newInstance") ||
                factory.equals("SchemaFactory.newInstance")) && vulTmp1){
            xxe = true;
        }

        if(factory.equals("DOMParser")){
            xxe = true;
        }
        return xxe;
    }
}
