package com.bracit.tendersense.util;

import com.bracit.tendersense.entity.CapabilityProfile;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.PastProject;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.Sector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LlmPromptBuilderTest {

    private static Organisation org() {
        return Organisation.builder().id(1L).name("BracIT").slug("bracit")
                .sectors(new ArrayList<>(List.of(Sector.IT_SERVICES, Sector.CONSULTANCY))).build();
    }

    private static CapabilityProfile profile() {
        CapabilityProfile p = CapabilityProfile.builder()
                .orgName("BRAC IT Services")
                .summary("Software and systems integration.")
                .services(new ArrayList<>(List.of("Custom software development", "ERP implementation")))
                .exclusions(new ArrayList<>(List.of("Supply of security guards and cleaners")))
                .pastProjects(new ArrayList<>())
                .updatedAt(Instant.parse("2026-09-10T08:00:00Z"))
                .build();
        p.getPastProjects().add(PastProject.builder().title("MIS for social protection")
                .client("Ministry").year(2024).description("x".repeat(1000)).build());
        return p;
    }

    @Test
    @DisplayName("the system prompt carries the whole company and nothing about any tender")
    void systemPromptIsTheCompany() {
        String system = LlmPromptBuilder.systemPrompt(org(), profile());

        assertTrue(system.contains("BRAC IT Services"));
        assertTrue(system.contains("- Custom software development"));
        assertTrue(system.contains("- ERP implementation"));
        assertTrue(system.contains("Supply of security guards"), "exclusions must reach the model");
        assertTrue(system.contains("IT services, Consultancy"), "sectors are labelled, not enum names");
        assertTrue(system.contains("90-100") && system.contains("0-9"), "the rubric bands are present");
        // Past project descriptions are clipped so a long reference cannot crowd out the rest.
        assertFalse(system.contains("x".repeat(LlmPromptBuilder.MAX_PROJECT + 1)));
    }

    @Test
    @DisplayName("the tender prompt is the tender: blank fields omitted, long ones clipped")
    void tenderPrompt() {
        Tender t = Tender.builder().id(9L).title("  Supply   of ERP  ")
                .description("d".repeat(LlmPromptBuilder.MAX_DESCRIPTION + 500))
                .cpvTop("Computer and related services").procuringEntity("   ")
                .build();

        String prompt = LlmPromptBuilder.tenderPrompt(t);

        assertTrue(prompt.contains("Title: Supply of ERP"), "whitespace is collapsed");
        assertTrue(prompt.contains("Category: Computer and related services"));
        assertFalse(prompt.contains("Procuring entity"), "a blank field is left out, not sent empty");
        assertFalse(prompt.contains("d".repeat(LlmPromptBuilder.MAX_DESCRIPTION + 1)));
    }

    @Test
    @DisplayName("the fingerprint changes when any input changes, and only then")
    void fingerprint() {
        Instant v1 = Instant.parse("2026-09-10T08:00:00Z");
        String base = LlmPromptBuilder.fingerprint(9L, "hash-a", v1, "ollama:qwen2.5:7b");

        assertEquals(64, base.length());
        assertEquals(base, LlmPromptBuilder.fingerprint(9L, "hash-a", v1, "ollama:qwen2.5:7b"));
        assertNotEquals(base, LlmPromptBuilder.fingerprint(9L, "hash-b", v1, "ollama:qwen2.5:7b"),
                "a revised tender");
        assertNotEquals(base, LlmPromptBuilder.fingerprint(9L, "hash-a", v1.plusSeconds(1), "ollama:qwen2.5:7b"),
                "an edited profile");
        assertNotEquals(base, LlmPromptBuilder.fingerprint(9L, "hash-a", v1, "ollama:llama3.1:8b"),
                "a different model");
    }
}
