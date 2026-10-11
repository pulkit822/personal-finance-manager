package com.pfm.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** End-to-end tests through the real security filter chain, controllers, services and H2. */
@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {
    private static final String PW = "securePass123";
    @Autowired MockMvc mvc;

    // ---------------- helpers ----------------
    private String email() { return "u" + System.nanoTime() + "@example.com"; }

    private String registerBody(String email, String password, String phone) {
        return "{\"username\":\"" + email + "\",\"password\":\"" + password
                + "\",\"fullName\":\"Test User\",\"phoneNumber\":\"" + phone + "\"}";
    }

    private void register(String email) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, PW, "+1234567890")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.userId").isNumber());
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + email + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    private MockHttpSession newSession() throws Exception {
        String e = email();
        register(e);
        return login(e);
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b,
                               MockHttpSession s, String body) throws Exception {
        b.session(s);
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(b);
    }

    private ResultActions doPost(MockHttpSession s, String url, String body) throws Exception { return send(post(url), s, body); }
    private ResultActions doPut(MockHttpSession s, String url, String body) throws Exception { return send(put(url), s, body); }
    private ResultActions doGet(MockHttpSession s, String url) throws Exception { return send(get(url), s, null); }
    private ResultActions doDelete(MockHttpSession s, String url) throws Exception { return send(delete(url), s, null); }

    private long idOf(ResultActions a) throws Exception {
        Number n = JsonPath.read(a.andReturn().getResponse().getContentAsString(), "$.id");
        return n.longValue();
    }

    private String txn(String amount, String date, String category, String desc) {
        return "{\"amount\":" + amount + ",\"date\":\"" + date + "\",\"category\":\"" + category
                + "\",\"description\":\"" + desc + "\"}";
    }

    // ---------------- authentication ----------------
    @Test
    void registrationValidationAndDuplicates() throws Exception {
        String e = email();
        register(e);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerBody(e, PW, "+1234567890")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerBody("not-an-email", PW, "+1234567890")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerBody(email(), "short", "+1234567890")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerBody(email(), PW, "abc")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PW + "\",\"fullName\":\"X\",\"phoneNumber\":\"+1234567890\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginLogoutAndSessionProtection() throws Exception {
        String e = email();
        register(e);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + e + "\",\"password\":\"wrongPassword\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ghost@example.com\",\"password\":\"password123\"}")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/categories")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));

        MockHttpSession s = login(e);
        doGet(s, "/api/categories").andExpect(status().isOk());
        doPost(s, "/api/auth/logout", null).andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Logout successful"));
        doGet(s, "/api/categories").andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    // ---------------- categories ----------------
    @Test
    void categoriesLifecycle() throws Exception {
        MockHttpSession s = newSession();
        doGet(s, "/api/categories").andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[?(@.name=='Salary')].type").value("INCOME"))
                .andExpect(jsonPath("$.categories[?(@.name=='Food')].isCustom").value(false));

        doPost(s, "/api/categories", "{\"name\":\"Freelance\",\"type\":\"INCOME\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.isCustom").value(true)).andExpect(jsonPath("$.custom").value(true));
        doPost(s, "/api/categories", "{\"name\":\"freelance\",\"type\":\"INCOME\"}").andExpect(status().isConflict());
        doPost(s, "/api/categories", "{\"name\":\"Food\",\"type\":\"EXPENSE\"}").andExpect(status().isConflict());
        doPost(s, "/api/categories", "{\"name\":\"Bad\",\"type\":\"INVALID\"}").andExpect(status().isBadRequest());
        doPost(s, "/api/categories", "{\"type\":\"INCOME\"}").andExpect(status().isBadRequest());

        doPost(s, "/api/transactions", txn("100.00", "2024-01-10", "Freelance", "gig")).andExpect(status().isCreated());
        doDelete(s, "/api/categories/Freelance").andExpect(status().isBadRequest());
        doDelete(s, "/api/categories/Food").andExpect(status().isForbidden());
        doDelete(s, "/api/categories/DoesNotExist").andExpect(status().isNotFound());

        doPost(s, "/api/categories", "{\"name\":\"Gym\",\"type\":\"EXPENSE\"}").andExpect(status().isCreated());
        doDelete(s, "/api/categories/Gym").andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Category deleted successfully"));
    }

    // ---------------- transactions ----------------
    @Test
    void transactionsCrudFiltersAndValidation() throws Exception {
        MockHttpSession s = newSession();
        long salary = idOf(doPost(s, "/api/transactions", txn("5000.00", "2024-01-15", "Salary", "January Salary"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.amount").value(5000.00)).andExpect(jsonPath("$.type").value("INCOME")));
        long rent = idOf(doPost(s, "/api/transactions", txn("1200.00", "2024-01-16", "Rent", "Rent"))
                .andExpect(jsonPath("$.type").value("EXPENSE")));
        doPost(s, "/api/transactions", txn("400.00", "2024-01-17", "Food", "Groceries")).andExpect(status().isCreated());

        // validation
        doPost(s, "/api/transactions", txn("100", "2024-01-18", "Nope", "x")).andExpect(status().isBadRequest());
        doPost(s, "/api/transactions", txn("100", LocalDate.now().plusDays(3).toString(), "Food", "x")).andExpect(status().isBadRequest());
        doPost(s, "/api/transactions", txn("-100", "2024-01-17", "Food", "x")).andExpect(status().isBadRequest());
        doPost(s, "/api/transactions", txn("0", "2024-01-17", "Food", "x")).andExpect(status().isBadRequest());
        doPost(s, "/api/transactions", txn("10.123", "2024-01-17", "Food", "x")).andExpect(status().isBadRequest());
        doPost(s, "/api/transactions", txn("100", "01-17-2024", "Food", "x")).andExpect(status().isBadRequest());
        doPost(s, "/api/transactions", "{ not json").andExpect(status().isBadRequest());

        // listing: newest first + filters
        doGet(s, "/api/transactions").andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions", hasSize(3)))
                .andExpect(jsonPath("$.transactions[0].date").value("2024-01-17"))
                .andExpect(jsonPath("$.transactions[2].date").value("2024-01-15"));
        doGet(s, "/api/transactions?category=Salary").andExpect(jsonPath("$.transactions", hasSize(1)));
        doGet(s, "/api/transactions?startDate=2024-01-15&endDate=2024-01-16").andExpect(jsonPath("$.transactions", hasSize(2)));
        doGet(s, "/api/transactions?type=EXPENSE").andExpect(jsonPath("$.transactions", hasSize(2)));
        doGet(s, "/api/transactions?categoryId=-1").andExpect(jsonPath("$.transactions", hasSize(0)));
        doGet(s, "/api/transactions?type=bogus").andExpect(status().isBadRequest());
        doGet(s, "/api/transactions?startDate=2024-02-01&endDate=2024-01-01").andExpect(status().isBadRequest());
        doGet(s, "/api/transactions?startDate=garbage").andExpect(status().isBadRequest());

        // update: date is ignored
        doPut(s, "/api/transactions/" + salary, "{\"date\":\"2024-01-20\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2024-01-15"));
        doPut(s, "/api/transactions/" + salary, "{\"amount\":5500.00,\"description\":\"Bonus\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(5500.00)).andExpect(jsonPath("$.description").value("Bonus"));
        doPut(s, "/api/transactions/" + salary, "{\"amount\":-5}").andExpect(status().isBadRequest());
        doPut(s, "/api/transactions/999999", "{\"amount\":10}").andExpect(status().isNotFound());
        doPut(s, "/api/transactions/abc", "{\"amount\":10}").andExpect(status().isBadRequest());

        doDelete(s, "/api/transactions/" + rent).andExpect(status().isOk());
        doDelete(s, "/api/transactions/" + rent).andExpect(status().isNotFound());
        doGet(s, "/api/transactions").andExpect(jsonPath("$.transactions", hasSize(2)));
    }

    // ---------------- goals + reports ----------------
    @Test
    void goalsTrackProgressAndRespondToDeletions() throws Exception {
        MockHttpSession s = newSession();
        doPost(s, "/api/transactions", txn("5000.00", "2024-01-15", "Salary", "pay")).andExpect(status().isCreated());
        long food = idOf(doPost(s, "/api/transactions", txn("400.00", "2024-01-17", "Food", "food")));
        String future = LocalDate.now().plusYears(1).toString();

        long goal = idOf(doPost(s, "/api/goals", "{\"goalName\":\"Fund\",\"targetAmount\":10000.00,\"targetDate\":\"" + future + "\",\"startDate\":\"2024-01-01\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentProgress").value(4600.00))
                .andExpect(jsonPath("$.progressPercentage").value(46.0))
                .andExpect(jsonPath("$.remainingAmount").value(5400.00)));

        doPut(s, "/api/goals/" + goal, "{\"targetAmount\":20000.00}").andExpect(status().isOk())
                .andExpect(jsonPath("$.progressPercentage").value(23.0));
        doPut(s, "/api/goals/" + goal, "{\"targetDate\":\"" + LocalDate.now().plusYears(2) + "\"}").andExpect(status().isOk());
        doPut(s, "/api/goals/" + goal, "{}").andExpect(status().isBadRequest());
        doPut(s, "/api/goals/999999", "{\"targetAmount\":5}").andExpect(status().isNotFound());

        doDelete(s, "/api/transactions/" + food).andExpect(status().isOk());
        doGet(s, "/api/goals/" + goal).andExpect(jsonPath("$.currentProgress").value(5000.00));

        // goal created without a start date defaults to today and has no progress yet
        doPost(s, "/api/goals", "{\"goalName\":\"Later\",\"targetAmount\":100,\"targetDate\":\"" + future + "\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.startDate").value(LocalDate.now(java.time.ZoneOffset.UTC).toString())).andExpect(jsonPath("$.currentProgress").value(0));
        doGet(s, "/api/goals").andExpect(jsonPath("$.goals", hasSize(2)));

        // validation
        doPost(s, "/api/goals", "{\"goalName\":\"X\",\"targetAmount\":100,\"targetDate\":\"2023-01-01\"}").andExpect(status().isBadRequest());
        doPost(s, "/api/goals", "{\"goalName\":\"X\",\"targetAmount\":-1,\"targetDate\":\"" + future + "\"}").andExpect(status().isBadRequest());
        doPost(s, "/api/goals", "{\"goalName\":\"X\",\"targetAmount\":0,\"targetDate\":\"" + future + "\"}").andExpect(status().isBadRequest());
        doPost(s, "/api/goals", "{\"goalName\":\"X\",\"targetAmount\":5,\"targetDate\":\"" + future + "\",\"startDate\":\"2099-01-01\"}").andExpect(status().isBadRequest());

        doDelete(s, "/api/goals/" + goal).andExpect(status().isOk());
        doDelete(s, "/api/goals/" + goal).andExpect(status().isNotFound());
    }

    @Test
    void reportsAggregateAndValidate() throws Exception {
        MockHttpSession s = newSession();
        doPost(s, "/api/categories", "{\"name\":\"Consulting\",\"type\":\"INCOME\"}").andExpect(status().isCreated());
        doPost(s, "/api/transactions", txn("5500.00", "2024-01-15", "Salary", "pay"));
        doPost(s, "/api/transactions", txn("800.00", "2024-01-25", "Consulting", "gig"));
        doPost(s, "/api/transactions", txn("450.00", "2024-01-17", "Food", "food"));
        doPost(s, "/api/transactions", txn("100.00", "2024-03-05", "Food", "food"));

        doGet(s, "/api/reports/monthly/2024/1").andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(1)).andExpect(jsonPath("$.year").value(2024))
                .andExpect(jsonPath("$.totalIncome.Salary").value(5500.00))
                .andExpect(jsonPath("$.totalIncome.Consulting").value(800.00))
                .andExpect(jsonPath("$.totalExpenses.Food").value(450.00))
                .andExpect(jsonPath("$.netSavings").value(5850.00));
        doGet(s, "/api/reports/monthly/2024/12").andExpect(jsonPath("$.netSavings").value(0));
        doGet(s, "/api/reports/yearly/2024").andExpect(jsonPath("$.netSavings").value(5750.00))
                .andExpect(jsonPath("$.totalExpenses.Food").value(550.00));
        doGet(s, "/api/reports/yearly/2023").andExpect(jsonPath("$.netSavings").value(0));
        doGet(s, "/api/reports/monthly/2024/13").andExpect(status().isBadRequest());
        doGet(s, "/api/reports/monthly/2024/0").andExpect(status().isBadRequest());
        doGet(s, "/api/reports/monthly/2024/abc").andExpect(status().isBadRequest());
    }

    // ---------------- isolation & misc errors ----------------
    @Test
    void usersCannotSeeOrChangeEachOthersData() throws Exception {
        MockHttpSession a = newSession();
        MockHttpSession b = newSession();
        doPost(a, "/api/categories", "{\"name\":\"OnlyMine\",\"type\":\"INCOME\"}").andExpect(status().isCreated());
        long txn = idOf(doPost(a, "/api/transactions", txn("10.00", "2024-01-01", "OnlyMine", "mine")));
        long goal = idOf(doPost(a, "/api/goals", "{\"goalName\":\"G\",\"targetAmount\":10,\"targetDate\":\"" + LocalDate.now().plusYears(1) + "\"}"));

        doGet(b, "/api/categories").andExpect(content().string(not(containsString("OnlyMine"))));
        doGet(b, "/api/transactions").andExpect(jsonPath("$.transactions", hasSize(0)));
        doGet(b, "/api/goals").andExpect(jsonPath("$.goals", hasSize(0)));
        doPost(b, "/api/transactions", txn("10", "2024-01-01", "OnlyMine", "x")).andExpect(status().isBadRequest());
        doPut(b, "/api/transactions/" + txn, "{\"amount\":1}").andExpect(status().isNotFound());
        doDelete(b, "/api/transactions/" + txn).andExpect(status().isNotFound());
        doDelete(b, "/api/categories/OnlyMine").andExpect(status().isNotFound());
        doGet(b, "/api/goals/" + goal).andExpect(status().isForbidden());
        doPut(b, "/api/goals/" + goal, "{\"targetAmount\":5}").andExpect(status().isForbidden());
        doDelete(b, "/api/goals/" + goal).andExpect(status().isForbidden());
    }

    @Test
    void unknownRoutesAndMethodsReturnClientErrors() throws Exception {
        MockHttpSession s = newSession();
        doGet(s, "/api/does-not-exist").andExpect(status().isNotFound());
        doPut(s, "/api/categories", "{}").andExpect(status().is4xxClientError());
        mvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().is4xxClientError());
    }
}
