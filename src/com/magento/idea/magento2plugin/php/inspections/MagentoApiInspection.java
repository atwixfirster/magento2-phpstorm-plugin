package com.magento.idea.magento2plugin.php.inspections;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocTag;
import com.jetbrains.php.lang.inspections.PhpInspection;
import com.jetbrains.php.lang.psi.elements.*;
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor;
import com.magento.idea.magento2plugin.php.module.MagentoModule;
import com.magento.idea.magento2plugin.php.module.ModuleManager;
import org.jetbrains.annotations.NotNull;

/**
 * Created by dkvashnin on 12/4/15.
 */
public class MagentoApiInspection extends PhpInspection {
    public static final String API_TAG = "@api";

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder problemsHolder, boolean isOnTheFly) {
        return new PhpElementVisitor() {
            @Override
            public void visitPhpMethodReference(MethodReference reference) {
                PsiElement referencedElement = reference.resolve();

                if(referencedElement instanceof Method) {
                    PhpClass phpClass = ((Method) referencedElement).getContainingClass();

                    if (phpClass == null) {
                        return;
                    }

                    if (!MagentoApiInspection.isValidReference(phpClass, reference.getElement())
                        || !MagentoApiInspection.isValidReference((Method) referencedElement, reference.getElement())) {
                        problemsHolder.registerProblem(reference, "Method #ref is not in module API", ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                    }
                }
            }

            @Override
            public void visitPhpClassReference(ClassReference reference) {
                PsiElement referencedElement = reference.resolve();

                if(referencedElement instanceof PhpClass) {
                    if (!MagentoApiInspection.isValidReference((PhpClass) referencedElement, reference.getElement())) {
                        problemsHolder.registerProblem(reference, "Class #ref is not in module API", ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                    }
                }
            }
        };
    }

    private static boolean isValidReference(PhpNamedElement referencedElement, PsiElement contextElement) {
        MagentoModule referenceSourceModule = getMagentoModule(referencedElement);
        MagentoModule currentModule = getMagentoModule(contextElement);


        if (!areDifferentModules(referenceSourceModule, currentModule)) {
            return true;
        }

        PhpDocComment docComment = referencedElement.getDocComment();
        if(docComment == null) {
            return false;
        }

        PhpDocTag[] elements = docComment.getTagElementsByName(API_TAG);
        return elements.length > 0;
    }

    private static MagentoModule getMagentoModule(PsiElement element) {
        ModuleManager moduleManager = ModuleManager.getInstance(element.getProject());
        return moduleManager.getModuleForFile(element.getContainingFile());
    }

    private static boolean areDifferentModules(MagentoModule magentoModule, MagentoModule currentPackage) {
        if (magentoModule == null) {
            return false;
        }

        return magentoModule != currentPackage;
    }
}
