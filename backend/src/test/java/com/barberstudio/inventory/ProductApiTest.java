package com.barberstudio.inventory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.barberstudio.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class ProductApiTest extends IntegrationTestBase {

    Cookie owner, barber, ownerB;

    @BeforeEach
    void setUp() throws Exception {
        truncateAll();
        long a = seedShop("shop-a");
        long b = seedShop("shop-b");
        seedUser(a, "a@test.com", "OWNER");
        seedUser(a, "barber@test.com", "BARBER");
        seedUser(b, "b@test.com", "OWNER");
        owner = login("a@test.com");
        barber = login("barber@test.com");
        ownerB = login("b@test.com");
    }

    private ResultActions send(Cookie c, String method, String url, String body) throws Exception {
        var req = switch (method) {
            case "PUT" -> put(url);
            case "GET" -> get(url);
            default -> post(url);
        };
        if (c != null) req = req.cookie(c);
        if (body != null) req = req.contentType(JSON).content(body);
        return mvc.perform(req);
    }

    private long create(Cookie c, String name) throws Exception {
        return id(postJson(c, "/api/products", "{\"name\":\"" + name + "\",\"price\":250}"));
    }

    private ResultActions move(Cookie c, long product, String body) throws Exception {
        return send(c, "POST", "/api/products/" + product + "/stock", body);
    }

    @Test
    void ownerCreatesProductWithZeroStock() throws Exception {
        send(owner, "POST", "/api/products", "{\"name\":\"Cera mate\",\"price\":250}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Cera mate"))
            .andExpect(jsonPath("$.price").value(250))
            .andExpect(jsonPath("$.stock").value(0))
            .andExpect(jsonPath("$.lowStock").value(true))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.visibleInShop").value(true));
    }

    @Test
    void purchaseAndAdjustmentUpdateStock() throws Exception {
        long p = create(owner, "Cera mate");
        move(owner, p, "{\"delta\":10,\"reason\":\"PURCHASE\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.stock").value(10));
        move(owner, p, "{\"delta\":-2,\"reason\":\"ADJUSTMENT\",\"note\":\"Rotura\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.stock").value(8));
        send(owner, "GET", "/api/products/" + p + "/movements", null).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].delta").value(-2))
            .andExpect(jsonPath("$[0].reason").value("ADJUSTMENT"))
            .andExpect(jsonPath("$[0].note").value("Rotura"))
            .andExpect(jsonPath("$[1].delta").value(10));
    }

    @Test
    void adjustmentRequiresNote() throws Exception {
        long p = create(owner, "Cera mate");
        move(owner, p, "{\"delta\":3,\"reason\":\"ADJUSTMENT\"}").andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail").value("Indicá el motivo del ajuste."));
        move(owner, p, "{\"delta\":3,\"reason\":\"ADJUSTMENT\",\"note\":\"   \"}").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void apiRejectsSystemReasons() throws Exception {
        long p = create(owner, "Cera mate");
        for (String r : new String[] {"SALE", "ORDER", "VOID", "ORDER_CANCEL"}) {
            move(owner, p, "{\"delta\":1,\"reason\":\"" + r + "\",\"note\":\"x\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Motivo no permitido."));
        }
    }

    @Test
    void zeroDeltaRejected() throws Exception {
        long p = create(owner, "Cera mate");
        move(owner, p, "{\"delta\":0,\"reason\":\"PURCHASE\"}").andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail").value("La cantidad no puede ser 0."));
    }

    @Test
    void purchaseMustBePositive() throws Exception {
        long p = create(owner, "Cera mate");
        move(owner, p, "{\"delta\":-1,\"reason\":\"PURCHASE\"}").andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail").value("La reposición tiene que sumar unidades."));
    }

    @Test
    void cannotLeaveNegativeStock() throws Exception {
        long p = create(owner, "Cera mate");
        move(owner, p, "{\"delta\":1,\"reason\":\"PURCHASE\"}").andExpect(status().isOk());
        move(owner, p, "{\"delta\":-2,\"reason\":\"ADJUSTMENT\",\"note\":\"Rotura\"}")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail").value("Stock insuficiente: quedan 1."));
        send(owner, "GET", "/api/products", null).andExpect(jsonPath("$[0].stock").value(1));
    }

    @Test
    void duplicateNameIgnoresCaseAndSpaces() throws Exception {
        create(owner, "Cera Mate");
        send(owner, "POST", "/api/products", "{\"name\":\" cera mate \",\"price\":100}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Ya existe un producto con ese nombre."));
    }

    @Test
    void renamingToExistingNameIsRejectedButKeepingOwnNameIsFine() throws Exception {
        create(owner, "Cera mate");
        long p = create(owner, "Aceite");
        send(owner, "PUT", "/api/products/" + p, "{\"name\":\"CERA MATE\",\"price\":100}").andExpect(status().isConflict());
        send(owner, "PUT", "/api/products/" + p, "{\"name\":\"Aceite\",\"price\":300,\"visibleInShop\":false}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.price").value(300))
            .andExpect(jsonPath("$.visibleInShop").value(false));
    }

    @Test
    void sameNameAllowedInOtherShop() throws Exception {
        create(owner, "Cera Mate");
        send(ownerB, "POST", "/api/products", "{\"name\":\"Cera Mate\",\"price\":250}").andExpect(status().isCreated());
    }

    @Test
    void invalidPriceRejected() throws Exception {
        send(owner, "POST", "/api/products", "{\"name\":\"A\",\"price\":-1}").andExpect(status().isBadRequest());
        send(owner, "POST", "/api/products", "{\"name\":\"B\",\"price\":10.555}").andExpect(status().isBadRequest());
        send(owner, "POST", "/api/products", "{\"name\":\"C\"}").andExpect(status().isBadRequest());
        send(owner, "POST", "/api/products", "{\"name\":\"  \",\"price\":10}").andExpect(status().isBadRequest());
    }

    @Test
    void otherShopGets404() throws Exception {
        long p = create(owner, "Cera mate");
        send(ownerB, "GET", "/api/products/" + p + "/movements", null).andExpect(status().isNotFound());
        send(ownerB, "PUT", "/api/products/" + p, "{\"name\":\"X\",\"price\":1}").andExpect(status().isNotFound());
        move(ownerB, p, "{\"delta\":5,\"reason\":\"PURCHASE\"}").andExpect(status().isNotFound());
        send(ownerB, "GET", "/api/products", null).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void barberCanListButNotManage() throws Exception {
        long p = create(owner, "Cera mate");
        send(barber, "GET", "/api/products", null).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        send(barber, "POST", "/api/products", "{\"name\":\"X\",\"price\":1}").andExpect(status().isForbidden());
        send(barber, "PUT", "/api/products/" + p, "{\"name\":\"X\",\"price\":1}").andExpect(status().isForbidden());
        move(barber, p, "{\"delta\":5,\"reason\":\"PURCHASE\"}").andExpect(status().isForbidden());
        send(barber, "GET", "/api/products/" + p + "/movements", null).andExpect(status().isForbidden());
    }

    @Test
    void lowStockFilter() throws Exception {
        long empty = create(owner, "A vacío");
        long two = create(owner, "B dos");
        long five = create(owner, "C cinco");
        move(owner, two, "{\"delta\":2,\"reason\":\"PURCHASE\"}");
        move(owner, five, "{\"delta\":5,\"reason\":\"PURCHASE\"}");
        send(owner, "GET", "/api/products?lowStock=true", null).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(empty))
            .andExpect(jsonPath("$[1].id").value(two));
        send(owner, "GET", "/api/products", null).andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void unauthenticatedGets401() throws Exception {
        send(null, "GET", "/api/products", null).andExpect(status().isUnauthorized());
    }
}
