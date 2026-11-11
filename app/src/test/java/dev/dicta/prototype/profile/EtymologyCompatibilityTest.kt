package dev.dicta.prototype.profile

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class EtymologyCompatibilityTest {
    private val root=File(System.getProperty("dicta.root",".."))
    private val schema=File(root,"profiles/default/entry.schema.json").readText()
    private val example=File(root,"profiles/default/example.json").readText()
    private val legacy=File(root,"app/src/androidTest/assets/t07/v100-popup.template.json").readText()
    private fun flatten(nodes:List<RenderNode>):List<RenderNode> = nodes.flatMap {listOf(it)+flatten(it.children)+it.items.flatMap(::flatten)}
    private fun check(template:String) {
        val result=ProfileEngine(schema,template).decodeAndRender(example)
        val nodes=flatten(result.nodes)
        assertTrue(nodes.any {it.label=="词源"})
        assertFalse(nodes.any {it.label.orEmpty().contains("AI 生成") || it.label.orEmpty().contains("核验") || it.value=="established"})
        assertTrue(nodes.any {it.style=="etymology" && !it.value.isNullOrBlank()})
        assertTrue(nodes.any {it.value?.contains("只持续一天的")==true})
    }
    @Test fun defaultShowsEtymologyContentWithoutDisclaimerOrSelfRating() = check(File(root,"profiles/default/popup.template.json").readText())
    @Test fun v100SnapshotGetsDisplayCompatibilityWithoutRewritingTemplate() {
        check(legacy)
        assertTrue(legacy.contains("AI 生成，未经外部检索核验"))
        assertTrue(legacy.contains("/etymology/certainty"))
    }
    @Test fun unrelatedCustomLabelsAndFieldsStayEditable() {
        val custom=legacy.replace("词源释义 · AI 生成，未经外部检索核验","我的词根笔记")
        val result=flatten(ProfileEngine(schema,custom).decodeAndRender(example).nodes)
        assertTrue(result.any {it.label=="我的词根笔记"})
        assertTrue(result.any {it.style=="etymology"})
    }
}
