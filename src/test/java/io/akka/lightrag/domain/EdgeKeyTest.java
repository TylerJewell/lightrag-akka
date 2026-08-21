package io.akka.lightrag.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.akka.lightrag.domain.Model.EdgeKey;
import org.junit.jupiter.api.Test;

/**
 * SPEC-001 R10 and R24 — a relation's identity, and the id it is stored under.
 *
 * <p>Tested here directly rather than only through the branches, because the branches
 * both build and look up keys through this one method: a key that stopped sorting would
 * still match itself everywhere, and every test above it would stay green.
 */
public class EdgeKeyTest {

  @Test
  public void aKeySortsItsPairWhicheverWayRoundItIsGiven() {
    assertEquals(EdgeKey.of("A", "B"), EdgeKey.of("B", "A"));
    assertEquals("A", EdgeKey.of("B", "A").a());
    assertEquals("B", EdgeKey.of("B", "A").b());
  }

  @Test
  public void aStorageIdRoundTripsANameHoldingReservedCharacters() {
    // A name is arbitrary extracted text. The vertical bar is reserved in an entity id
    // and the comma is this id's own separator, so both have to survive as content.
    var key = EdgeKey.of("A|B, LTD", "ACME");
    var back = EdgeKey.fromStorageId(key.storageId());

    assertEquals(key, back);
    assertEquals("A|B, LTD", back.b());
  }

  @Test
  public void aStorageIdHoldsNoReservedCharacterOfItsOwn() {
    var id = EdgeKey.of("A|B", "C|D").storageId();

    assertEquals(-1, id.indexOf('|'));
    assertEquals(id.indexOf(','), id.lastIndexOf(','));
  }
}
