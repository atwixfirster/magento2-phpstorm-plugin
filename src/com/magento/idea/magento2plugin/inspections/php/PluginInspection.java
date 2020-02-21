package com.magento.idea.magento2plugin.inspections.php;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.inspections.PhpInspection;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.Parameter;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor;
import com.magento.idea.magento2plugin.inspections.php.plugin.inspection.PluginInspector;
import com.magento.idea.magento2plugin.inspections.php.plugin.inspection.PluginInspectorFactory;
import com.magento.idea.magento2plugin.stubs.indexes.PluginIndex;
import org.jetbrains.annotations.NotNull;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class PluginInspection extends PhpInspection {

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder problemsHolder, boolean b) {
        return new PhpElementVisitor() {
            private static final String pluginOnNotPublicMethodProblemDescription = "You can't declare a plugin for the not public method";
            private static final String pluginOnFinalClassProblemDescription = "You can't declare a plugin for the final class!";
            private static final String pluginOnFinalMethodProblemDescription = "You can't declare a plugin for the final method!";
            private static final String pluginOnStaticMethodProblemDescription = "You can't declare a plugin for the static method!";
            private static final String pluginOnConstructorMethodProblemDescription = "You can't declare a plugin for the __construct method!";
            private static final String toFewArgumentsProblemDescription = "To few arguments in the plugin!";
            private static final String toManyArgumentsProblemDescription = "To many arguments in the plugin";

            public void visitPhpMethod(Method pluginMethod) {
                PluginInspector pluginInspector = PluginInspectorFactory.create(pluginMethod);
                if (pluginInspector == null) {
                    return;
                }

                PsiElement parentClass = pluginMethod.getParent();
                if (!(parentClass instanceof PhpClass)) {
                    return;
                }
                String currentClass = ((PhpClass) parentClass).getFQN().substring(1);
                PsiElement currentClassNameIdentifier = ((PhpClass) parentClass).getNameIdentifier();

                Collection<String> allKeys = FileBasedIndex.getInstance()
                        .getAllKeys(PluginIndex.KEY, problemsHolder.getProject());

                for (String targetClassName: allKeys) {
                    List<Set<String>> pluginsList = FileBasedIndex.getInstance()
                            .getValues(com.magento.idea.magento2plugin.stubs.indexes.PluginIndex.KEY, targetClassName, GlobalSearchScope.allScope(problemsHolder.getProject()));
                    if (pluginsList.isEmpty()) {
                        return;
                    }
                    for (Set<String> plugins : pluginsList) {
                        for (String plugin : plugins) {
                            if (!plugin.equals(currentClass)){
                                continue;
                            }

                            PhpIndex phpIndex = PhpIndex.getInstance(problemsHolder.getProject());
                            Collection<PhpClass> targets = phpIndex.getClassesByFQN(targetClassName);
                            if (targets.isEmpty()) {
                                return;
                            }
                            for (PhpClass target : targets) {
                                if (target.isFinal()) {
                                    ProblemDescriptor[] currentResults = problemsHolder.getResultsArray();
                                    int finalClassProblems = getFinalClassProblems(currentResults);
                                    if (finalClassProblems == 0) {
                                        problemsHolder.registerProblem(currentClassNameIdentifier, pluginOnFinalClassProblemDescription, ProblemHighlightType.ERROR);
                                    }
                                }

                                String targetClassMethodName = pluginInspector.getTargetMethodName();

                                if (targetClassMethodName.equals("__construct")) {
                                    problemsHolder.registerProblem(pluginMethod.getNameIdentifier(), pluginOnConstructorMethodProblemDescription, ProblemHighlightType.ERROR);
                                }

                                Method targetMethod = target.findMethodByName(targetClassMethodName);
                                if (targetMethod == null) {
                                    return;
                                }
                                if (targetMethod.isFinal()) {
                                    problemsHolder.registerProblem(pluginMethod.getNameIdentifier(), pluginOnFinalMethodProblemDescription, ProblemHighlightType.ERROR);
                                }
                                if (targetMethod.isStatic()) {
                                    problemsHolder.registerProblem(pluginMethod.getNameIdentifier(), pluginOnStaticMethodProblemDescription, ProblemHighlightType.ERROR);
                                }
                                if (!targetMethod.getAccess().toString().equals("public")) {
                                    problemsHolder.registerProblem(pluginMethod.getNameIdentifier(), pluginOnNotPublicMethodProblemDescription, ProblemHighlightType.ERROR);
                                }

                                Parameter[] targetMethodParameters = targetMethod.getParameters();
                                int targetMethodArgumentsCount = targetMethodParameters.length;

                                if (!pluginInspector.inspectMaximumArguments(targetMethodArgumentsCount)) {
                                    problemsHolder.registerProblem(pluginMethod.getNameIdentifier(), toManyArgumentsProblemDescription, ProblemHighlightType.ERROR);
                                }
                                if (!pluginInspector.inspectMinimumArguments(targetMethodArgumentsCount)) {
                                    problemsHolder.registerProblem(pluginMethod.getNameIdentifier(), toFewArgumentsProblemDescription, ProblemHighlightType.ERROR);
                                }
                            }
                        }
                    }
                }
            }

            private int getFinalClassProblems(ProblemDescriptor[] currentResults) {
                int finalClassProblems = 0;
                for (ProblemDescriptor currentProblem: currentResults) {
                    if (currentProblem.getDescriptionTemplate().equals(pluginOnFinalClassProblemDescription)) {
                        finalClassProblems++;
                    }
                }
                return finalClassProblems;
            }
        };
    }
}
