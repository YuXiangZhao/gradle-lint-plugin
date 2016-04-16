package com.netflix.nebula.lint.plugin

import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import org.codenarc.rule.Rule
import org.gradle.api.Project

class LintRuleRegistry {
    static ClassLoader classLoader = null

    private static LintRuleDescriptor findRuleDescriptor(String ruleId) {
        assert classLoader != null
        URL resource = classLoader.getResource(String.format("META-INF/lint-rules/%s.properties", ruleId))
        return resource ? new LintRuleDescriptor(resource) : null
    }

    static List<String> findRules(String ruleId) {
        assert classLoader != null
        def ruleDescriptor = findRuleDescriptor(ruleId)
        if (ruleDescriptor == null)
            return []

        if(ruleDescriptor.implementationClassName)
            return [ruleId]
        else
            return (ruleDescriptor.includes?.collect { findRules(it as String) }?.flatten() ?: []) as List<String>
    }

    List<Rule> buildRules(String ruleId, Project project) {
        assert classLoader != null
        def ruleDescriptor = findRuleDescriptor(ruleId)
        if (ruleDescriptor == null)
            return []

        def implClassName = ruleDescriptor.implementationClassName
        def includes = ruleDescriptor.includes

        if (!implClassName && includes.isEmpty()) {
            throw new InvalidRuleException(String.format("No implementation class or includes specified for rule '%s' in %s.", ruleId, ruleDescriptor))
        }

        def included = includes.collect { buildRules(it as String, project) }.flatten() as List<Rule>

        if(implClassName) {
            try {
                Rule r = (Rule) classLoader.loadClass(implClassName).newInstance()
                if(r instanceof GradleModelAware) {
                    (r as GradleModelAware).project = project
                }

                if(r instanceof GradleLintRule) {
                    r.ruleId = ruleId
                    r.buildFile = project.buildFile
                }
                return included + r
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                throw new InvalidRuleException(String.format(
                        "Could not find or load implementation class '%s' for rule '%s' specified in %s.", implClassName, ruleId, ruleDescriptor), e)
            }
        } else {
            return included
        }
    }
}
