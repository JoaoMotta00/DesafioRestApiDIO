package me.dio;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste ponta a ponta da API (perfil padrão "dev": H2 em memória).
 * Cada teste usa um número de conta diferente, pois o banco é compartilhado entre eles.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static String userJson(String accountNumber) {
        return """
                {
                  "name": "Naruto",
                  "account": { "number": "%s", "agency": "0001", "balance": 1500.50, "limit": 500.00 },
                  "card": { "number": "xxxx xxxx xxxx %s", "limit": 1000.00 },
                  "features": [ { "icon": "pix.svg", "description": "PIX" } ],
                  "news": [ { "icon": "credit.svg", "description": "Invista!" } ]
                }
                """.formatted(accountNumber, accountNumber);
    }

    @Test
    void shouldCreateAndFindUser() throws Exception {
        var created = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("1001")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();

        Number id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/users/{id}", id.longValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Naruto"))
                .andExpect(jsonPath("$.account.number").value("1001"))
                .andExpect(jsonPath("$.features[0].description").value("PIX"))
                .andExpect(jsonPath("$.news[0].description").value("Invista!"));
    }

    @Test
    void shouldRejectDuplicatedAccountNumber() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("2002")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("2002")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string("This Account number already exists."));
    }

    @Test
    void shouldReturnNotFoundForUnknownUser() throws Exception {
        mockMvc.perform(get("/users/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Resource ID not found."));
    }
}
