package com.redgreenrefactor.tool;

import com.anthropic.models.messages.Tool;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TDDToolsTest {

    @Test
    void getAllTools_returnsAllSixTools() {
        List<Tool> tools = TDDTools.getAllTools();

        assertThat(tools).hasSize(6);
        assertThat(tools).extracting(Tool::name)
                .containsExactlyInAnyOrder("Read", "Write", "Edit", "Bash", "Glob", "Grep");
    }

    @Test
    void createReadTool_hasCorrectSchema() {
        Tool tool = TDDTools.createReadTool();

        assertThat(tool.name()).isEqualTo("Read");
        assertThat(tool.description()).isNotNull();
        assertThat(tool.inputSchema()).isNotNull();
    }

    @Test
    void createWriteTool_hasCorrectSchema() {
        Tool tool = TDDTools.createWriteTool();

        assertThat(tool.name()).isEqualTo("Write");
        assertThat(tool.description()).isNotNull();
        assertThat(tool.inputSchema()).isNotNull();
    }

    @Test
    void createEditTool_hasCorrectSchema() {
        Tool tool = TDDTools.createEditTool();

        assertThat(tool.name()).isEqualTo("Edit");
        assertThat(tool.description()).isNotNull();
        assertThat(tool.inputSchema()).isNotNull();
    }

    @Test
    void createBashTool_hasCorrectSchema() {
        Tool tool = TDDTools.createBashTool();

        assertThat(tool.name()).isEqualTo("Bash");
        assertThat(tool.description()).isNotNull();
        assertThat(tool.inputSchema()).isNotNull();
    }

    @Test
    void createGlobTool_hasCorrectSchema() {
        Tool tool = TDDTools.createGlobTool();

        assertThat(tool.name()).isEqualTo("Glob");
        assertThat(tool.description()).isNotNull();
        assertThat(tool.inputSchema()).isNotNull();
    }

    @Test
    void createGrepTool_hasCorrectSchema() {
        Tool tool = TDDTools.createGrepTool();

        assertThat(tool.name()).isEqualTo("Grep");
        assertThat(tool.description()).isNotNull();
        assertThat(tool.inputSchema()).isNotNull();
    }
}
