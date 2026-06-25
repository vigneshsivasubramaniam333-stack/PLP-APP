package com.plp.lending.payment;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayuHashUtilTest {

  private static final String KEY = "g8yc8J";
  private static final String SALT = "BBc2U41mOZPLuteAYBW9wJ2Lnm9V9rQV";

  @Test
  void forwardHash_matchesPayuDocumentedExample() {
    String hash = PayuHashUtil.forwardHash(
        KEY,
        "PLP-47d864c4-1782205145108",
        "1000.00",
        "PLP Invoice Repayment",
        "TEST BORROWER01",
        "testborrower01@bl.com",
        "47d864c4-439a-415b-8235-1fe968ac9c02",
        SALT);
    assertEquals(
        "eaf794fa1b23836ac49408fa33a312de04cceda78110aecd40b6d6eadc5c3dad480310f6ec9a05347c71cfb478b4b351f3fd500a654f2b82dd92a02f90f4d46b",
        hash);
  }

  @Test
  void forwardHash_isDeterministicSha512() {
    String hash = PayuHashUtil.forwardHash(
        KEY,
        "PLP-test-123",
        "100.00",
        "PLP Invoice Repayment",
        "Borrower",
        "test@example.com",
        "09a01a25-1965-41cf-82b5-2d76a8b15c93",
        SALT);
    assertEquals(128, hash.length());
    assertEquals(hash, PayuHashUtil.forwardHash(
        KEY,
        "PLP-test-123",
        "100.00",
        "PLP Invoice Repayment",
        "Borrower",
        "test@example.com",
        "09a01a25-1965-41cf-82b5-2d76a8b15c93",
        SALT));
  }

  @Test
  void reverseHashFromCallback_matchesPayuSuccessExample() {
    Map<String, String> callback = new LinkedHashMap<>();
    callback.put("status", "success");
    callback.put("udf1", "47d864c4-439a-415b-8235-1fe968ac9c02");
    callback.put("email", "testborrower01@bl.com");
    callback.put("firstname", "TEST BORROWER01");
    callback.put("productinfo", "PLP Invoice Repayment");
    callback.put("amount", "1000.00");
    callback.put("txnid", "PLP-47d864c4-1782205768105");
    callback.put("key", KEY);
    callback.put(
        "hash",
        "1befbb0f40e4600c53aefe73d00a313956ae5a013593143d3c1f892d7ea8c6d6607dde548e0a0c740ad20d1cd8cbaee20f96ab9c9600bef5cfdcdaf73883ea61");

    assertEquals(
        callback.get("hash"),
        PayuHashUtil.reverseHashFromCallback(callback, SALT, KEY));
    assertTrue(PayuHashUtil.reverseHashMatches(callback, SALT, KEY, callback.get("hash")));
  }

  @Test
  void reverseHashFromCallback_usesUdfFieldsAndSixEmptyPipes() {
    Map<String, String> callback = new LinkedHashMap<>();
    callback.put("status", "success");
    callback.put("udf1", "09a01a25-1965-41cf-82b5-2d76a8b15c93");
    callback.put("email", "test@example.com");
    callback.put("firstname", "Borrower");
    callback.put("productinfo", "PLP Invoice Repayment");
    callback.put("amount", "100.00");
    callback.put("txnid", "PLP-test-123");
    callback.put("key", KEY);

    String reverse = PayuHashUtil.reverseHashFromCallback(callback, SALT, KEY);

    Map<String, String> withoutUdf = new LinkedHashMap<>(callback);
    withoutUdf.remove("udf1");
    String withoutUdfHash = PayuHashUtil.reverseHashFromCallback(withoutUdf, SALT, KEY);

    assertNotEquals(reverse, withoutUdfHash, "udf1 must be included in reverse hash");
    assertEquals(128, reverse.length());
  }

  @Test
  void commandHash_isDeterministicSha512() {
    String hash = PayuHashUtil.commandHash(KEY, "verify_payment", "PLP-test-123", SALT);
    assertEquals(128, hash.length());
    assertEquals(hash, PayuHashUtil.commandHash(KEY, "verify_payment", "PLP-test-123", SALT));
  }

  @Test
  void reverseHashFromCallback_includesAdditionalChargesWhenPresent() {
    Map<String, String> callback = baseCallback();
    callback.put("additionalCharges", "10.00");
    String withCharges = PayuHashUtil.reverseHashFromCallback(callback, SALT, KEY);

    Map<String, String> without = baseCallback();
    String withoutCharges = PayuHashUtil.reverseHashFromCallback(without, SALT, KEY);

    assertNotEquals(withCharges, withoutCharges);
  }

  private static Map<String, String> baseCallback() {
    Map<String, String> callback = new LinkedHashMap<>();
    callback.put("status", "success");
    callback.put("udf1", "borrower-id");
    callback.put("email", "test@example.com");
    callback.put("firstname", "Borrower");
    callback.put("productinfo", "PLP Invoice Repayment");
    callback.put("amount", "100.00");
    callback.put("txnid", "PLP-test-123");
    callback.put("key", KEY);
    return callback;
  }
}
