package com.hominux.compress4j.semver

import java.io.File
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class JapicmpReportTest {

    @TempDir
    lateinit var tempDir: Path

    private fun report(body: String): File =
        tempDir.resolve("japicmp.xml").toFile().apply {
            writeText("""<?xml version="1.0" encoding="UTF-8"?><japicmp>$body</japicmp>""")
        }

    @Test
    fun `a missing report is refused`() {
        assertThatThrownBy { JapicmpReport.changes(tempDir.resolve("absent.xml").toFile()) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("absent.xml")
    }

    @Test
    fun `an unchanged class yields no changes`() {
        val xml = report(
            """<class fullyQualifiedName="com.hominux.compress4j.Foo" changeStatus="UNCHANGED"
                      binaryCompatible="true" sourceCompatible="true"/>"""
        )

        assertThat(JapicmpReport.changes(xml)).isEmpty()
    }

    @Test
    fun `a new class requires a minor bump`() {
        val xml = report(
            """<class fullyQualifiedName="com.hominux.compress4j.Foo" changeStatus="NEW"
                      binaryCompatible="true" sourceCompatible="true"/>"""
        )

        assertThat(JapicmpReport.changes(xml)).singleElement().satisfies({
            assertThat(it.requiredBump).isEqualTo(SemverBump.MINOR)
            assertThat(it.path).isEqualTo("com.hominux.compress4j.Foo")
            assertThat(it.reason).isEqualTo("new public API")
        })
    }

    @Test
    fun `a binary incompatible member requires a major bump`() {
        val xml = report(
            """<class fullyQualifiedName="com.hominux.compress4j.Foo" changeStatus="MODIFIED"
                      binaryCompatible="true" sourceCompatible="true">
                 <method name="removed" changeStatus="REMOVED" binaryCompatible="false" sourceCompatible="false">
                   <compatibilityChanges><compatibilityChange type="METHOD_REMOVED"/></compatibilityChanges>
                 </method>
               </class>"""
        )

        assertThat(JapicmpReport.changes(xml)).singleElement().satisfies({
            assertThat(it.requiredBump).isEqualTo(SemverBump.MAJOR)
            assertThat(it.path).isEqualTo("com.hominux.compress4j.Foo#removed")
            assertThat(it.reason).contains("binary incompatible", "METHOD_REMOVED")
        })
    }

    @Test
    fun `a source incompatible member requires a major bump`() {
        val xml = report(
            """<class fullyQualifiedName="com.hominux.compress4j.Foo" changeStatus="MODIFIED"
                      binaryCompatible="true" sourceCompatible="true">
                 <method name="narrowed" changeStatus="MODIFIED" binaryCompatible="true" sourceCompatible="false"/>
               </class>"""
        )

        assertThat(JapicmpReport.changes(xml)).singleElement().satisfies({
            assertThat(it.requiredBump).isEqualTo(SemverBump.MAJOR)
            assertThat(it.reason).contains("source incompatible")
        })
    }

    @Test
    fun `a newly deprecated member requires a minor bump`() {
        val xml = report(
            """<class fullyQualifiedName="com.hominux.compress4j.Foo" changeStatus="MODIFIED"
                      binaryCompatible="true" sourceCompatible="true">
                 <method name="old" changeStatus="MODIFIED" binaryCompatible="true" sourceCompatible="true">
                   <compatibilityChanges>
                     <compatibilityChange type="ANNOTATION_DEPRECATED_ADDED"/>
                   </compatibilityChanges>
                 </method>
               </class>"""
        )

        assertThat(JapicmpReport.changes(xml)).singleElement().satisfies({
            assertThat(it.requiredBump).isEqualTo(SemverBump.MINOR)
            assertThat(it.reason).isEqualTo("newly deprecated")
        })
    }

    @Test
    fun `the enclosing class is not reported when a child already asks for the same bump`() {
        val xml = report(
            """<class fullyQualifiedName="com.hominux.compress4j.Foo" changeStatus="MODIFIED"
                      binaryCompatible="false" sourceCompatible="false">
                 <method name="removed" changeStatus="REMOVED" binaryCompatible="false" sourceCompatible="false"/>
               </class>"""
        )

        assertThat(JapicmpReport.changes(xml)).singleElement().satisfies({
            assertThat(it.path).isEqualTo("com.hominux.compress4j.Foo#removed")
        })
    }

    @Test
    fun `the enclosing class is reported when it outranks its children`() {
        val xml = report(
            """<class fullyQualifiedName="com.hominux.compress4j.Foo" changeStatus="MODIFIED"
                      binaryCompatible="false" sourceCompatible="false">
                 <method name="added" changeStatus="NEW" binaryCompatible="true" sourceCompatible="true"/>
               </class>"""
        )

        assertThat(JapicmpReport.changes(xml)).extracting<SemverBump> { it.requiredBump }
            .containsExactlyInAnyOrder(SemverBump.MINOR, SemverBump.MAJOR)
    }

    @Test
    fun `the highest bump across a whole report wins`() {
        val xml = report(
            """<class fullyQualifiedName="com.hominux.compress4j.Foo" changeStatus="NEW"
                      binaryCompatible="true" sourceCompatible="true"/>
               <class fullyQualifiedName="com.hominux.compress4j.Bar" changeStatus="REMOVED"
                      binaryCompatible="false" sourceCompatible="false"/>"""
        )

        val bump = SemverBump.highestOf(JapicmpReport.changes(xml).map { it.requiredBump })

        assertThat(bump).isEqualTo(SemverBump.MAJOR)
    }

    @Test
    fun `elements that do not describe API members are ignored`() {
        val xml = report("""<parameter name="arg0" changeStatus="MODIFIED" binaryCompatible="false"/>""")

        assertThat(JapicmpReport.changes(xml)).isEmpty()
    }

    @Test
    fun `a report declaring a doctype is refused`() {
        val xml = tempDir.resolve("evil.xml").toFile().apply {
            writeText(
                """<?xml version="1.0"?>
                   <!DOCTYPE japicmp [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                   <japicmp/>"""
            )
        }

        assertThatThrownBy { JapicmpReport.changes(xml) }.hasMessageContaining("DOCTYPE")
    }
}
