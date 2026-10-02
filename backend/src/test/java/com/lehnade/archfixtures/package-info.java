/**
 * Architecture-rule fixtures: classes that deliberately violate (or legitimately satisfy) the
 * rules of {@code com.lehnade.mbia.architecture.ArchitectureRules}. Each {@code ruleN} package is
 * its own root and is only read by {@code ArchitectureRulesViolationTest}. Kept outside
 * {@code com.lehnade.mbia} so that Spring never scans them.
 */
package com.lehnade.archfixtures;
