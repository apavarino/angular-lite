package dev.angularlite;

import com.intellij.codeInspection.InspectionSuppressor;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.psi.PsiElement;

/** Angular's template checker owns element validation in Angular HTML files. */
public final class HtmlInspectionSuppressor implements InspectionSuppressor {
    @Override public boolean isSuppressedFor(PsiElement element, String toolId) {
        if (element == null) return false;
        if ("CheckEmptyScriptTag".equals(toolId)) {
            var tag = com.intellij.psi.util.PsiTreeUtil.getParentOfType(element, com.intellij.psi.xml.XmlTag.class, false);
            if (tag == null || com.intellij.xml.util.HtmlUtil.isHtml5Tag(tag.getName())) return false;
        } else if (!"HtmlUnknownTag".equals(toolId)) return false;
        var file = element.getContainingFile();
        if (file == null) return false;
        var virtualFile = file.getVirtualFile();
        if (virtualFile == null || !"html".equalsIgnoreCase(virtualFile.getExtension())) return false;
        return new AngularMatcher().match(virtualFile, element.getProject());
    }

    @Override public SuppressQuickFix[] getSuppressActions(PsiElement element, String toolId) {
        return new SuppressQuickFix[0];
    }
}
