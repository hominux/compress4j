package com.hominux.compress4j.semver

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

data class ApiChange(val kind: String, val path: String, val reason: String, val requiredBump: SemverBump) {
    override fun toString(): String = "$kind $path: $reason, requires ${requiredBump.name.lowercase()}"
}

object JapicmpReport {

    private const val DEPRECATION = "ANNOTATION_DEPRECATED_ADDED"

    /** Elements of a japicmp report that describe API members; their children only carry the details of a change. */
    private val apiElements = setOf("class", "method", "constructor", "field", "superclass", "interface")

    /** Reads a japicmp XML report and returns the API changes that ask for a version bump, most specific first. */
    fun changes(report: File): List<ApiChange> {
        check(report.isFile) { "The japicmp report ${report.path} was not produced, cannot verify the API compatibility" }
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val document = report.inputStream().use { factory.newDocumentBuilder().parse(it) }
        return collect(document.documentElement, "").distinct()
    }

    private fun collect(element: Element, path: String): List<ApiChange> {
        val ownPath = pathOf(element, path)
        val fromChildren = element.childElements().flatMap { collect(it, ownPath) }
        val own = changeOf(element, ownPath) ?: return fromChildren
        return if (fromChildren.any { it.requiredBump >= own.requiredBump }) fromChildren else fromChildren + own
    }

    private fun changeOf(element: Element, path: String): ApiChange? {
        if (element.tagName !in apiElements) return null
        val status = element.getAttribute("changeStatus").ifEmpty { return null }.lowercase()
        val incompatibilities = element.compatibilityChanges()
        val details = incompatibilities.ifEmpty { listOf(status) }.joinToString(", ")
        return when {
            element.getAttribute("binaryCompatible") == "false" ->
                ApiChange(element.tagName, path, "binary incompatible ($details)", SemverBump.MAJOR)
            element.getAttribute("sourceCompatible") == "false" ->
                ApiChange(element.tagName, path, "source incompatible ($details)", SemverBump.MAJOR)
            status == "new" -> ApiChange(element.tagName, path, "new public API", SemverBump.MINOR)
            incompatibilities.contains(DEPRECATION) ->
                ApiChange(element.tagName, path, "newly deprecated", SemverBump.MINOR)
            status == "unchanged" -> null
            else -> ApiChange(element.tagName, path, status, SemverBump.PATCH)
        }
    }

    private fun pathOf(element: Element, parentPath: String): String {
        val name = element.getAttribute("fullyQualifiedName").ifEmpty { element.getAttribute("name") }
        return when {
            name.isEmpty() -> parentPath
            parentPath.isEmpty() -> name
            else -> "$parentPath#$name"
        }
    }

    private fun Element.compatibilityChanges(): List<String> = childElements()
        .filter { it.tagName == "compatibilityChanges" }
        .flatMap { it.childElements() }
        .map { it.getAttribute("type") }
        .filter { it.isNotEmpty() }

    private fun Element.childElements(): List<Element> {
        val children = childNodes
        return (0 until children.length)
            .map { children.item(it) }
            .filter { it.nodeType == Node.ELEMENT_NODE }
            .map { it as Element }
    }
}
