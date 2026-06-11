package org.duollectis.mapart.tools.converter;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StaircaseModeTest {

	// ─── isFlat ──────────────────────────────────────────────────────────────────

	@Test
	void isFlat_FLAT_returnsTrue() {
		assertThat(StaircaseMode.FLAT.isFlat()).isTrue();
	}

	@Test
	void isFlat_FLAT_DARK_returnsTrue() {
		assertThat(StaircaseMode.FLAT_DARK.isFlat()).isTrue();
	}

	@Test
	void isFlat_FLAT_LIGHT_returnsTrue() {
		assertThat(StaircaseMode.FLAT_LIGHT.isFlat()).isTrue();
	}

	@Test
	void isFlat_STAIRCASE_returnsFalse() {
		assertThat(StaircaseMode.STAIRCASE.isFlat()).isFalse();
	}

	@Test
	void isFlat_VALLEY_returnsFalse() {
		assertThat(StaircaseMode.VALLEY.isFlat()).isFalse();
	}

	@Test
	void isFlat_DARK_returnsFalse() {
		assertThat(StaircaseMode.DARK.isFlat()).isFalse();
	}

	// ─── getAllowedBrightnesses ───────────────────────────────────────────────────

	@Test
	void getAllowedBrightnesses_STAIRCASE_containsAllThree() {
		Set<Brightness> allowed = StaircaseMode.STAIRCASE.getAllowedBrightnesses();

		assertThat(allowed).containsExactlyInAnyOrder(Brightness.LOW, Brightness.NORMAL, Brightness.HIGH);
	}

	@Test
	void getAllowedBrightnesses_VALLEY_containsAllThree() {
		Set<Brightness> allowed = StaircaseMode.VALLEY.getAllowedBrightnesses();

		assertThat(allowed).containsExactlyInAnyOrder(Brightness.LOW, Brightness.NORMAL, Brightness.HIGH);
	}

	@Test
	void getAllowedBrightnesses_DARK_containsOnlyLow() {
		Set<Brightness> allowed = StaircaseMode.DARK.getAllowedBrightnesses();

		assertThat(allowed).containsOnly(Brightness.LOW);
	}

	@Test
	void getAllowedBrightnesses_LIGHT_containsOnlyHigh() {
		Set<Brightness> allowed = StaircaseMode.LIGHT.getAllowedBrightnesses();

		assertThat(allowed).containsOnly(Brightness.HIGH);
	}

	@Test
	void getAllowedBrightnesses_FLAT_containsOnlyNormal() {
		Set<Brightness> allowed = StaircaseMode.FLAT.getAllowedBrightnesses();

		assertThat(allowed).containsOnly(Brightness.NORMAL);
	}

	@Test
	void getAllowedBrightnesses_FLAT_DARK_containsOnlyLow() {
		Set<Brightness> allowed = StaircaseMode.FLAT_DARK.getAllowedBrightnesses();

		assertThat(allowed).containsOnly(Brightness.LOW);
	}

	@Test
	void getAllowedBrightnesses_FLAT_LIGHT_containsOnlyHigh() {
		Set<Brightness> allowed = StaircaseMode.FLAT_LIGHT.getAllowedBrightnesses();

		assertThat(allowed).containsOnly(Brightness.HIGH);
	}

	@Test
	void getAllowedBrightnesses_UPWARDS_ONLY_containsNormalAndHigh() {
		Set<Brightness> allowed = StaircaseMode.UPWARDS_ONLY.getAllowedBrightnesses();

		assertThat(allowed).containsExactlyInAnyOrder(Brightness.NORMAL, Brightness.HIGH);
	}

	@Test
	void getAllowedBrightnesses_REVERSE_UPWARDS_ONLY_containsLowAndNormal() {
		Set<Brightness> allowed = StaircaseMode.REVERSE_UPWARDS_ONLY.getAllowedBrightnesses();

		assertThat(allowed).containsExactlyInAnyOrder(Brightness.LOW, Brightness.NORMAL);
	}

	// ─── normalize ───────────────────────────────────────────────────────────────

	@Test
	void isNormalize_VALLEY_returnsTrue() {
		assertThat(StaircaseMode.VALLEY.isNormalize()).isTrue();
	}

	@Test
	void isNormalize_STAIRCASE_returnsFalse() {
		assertThat(StaircaseMode.STAIRCASE.isNormalize()).isFalse();
	}
}
