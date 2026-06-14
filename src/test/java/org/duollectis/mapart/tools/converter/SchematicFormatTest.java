package org.duollectis.mapart.tools.converter;

import org.duollectis.mapart.tools.converter.schematic.LitematicSchematicReader;
import org.duollectis.mapart.tools.converter.schematic.LitematicSchematicWriter;
import org.duollectis.mapart.tools.converter.schematic.NbtSchematicReader;
import org.duollectis.mapart.tools.converter.schematic.NbtSchematicWriter;
import org.duollectis.mapart.tools.converter.schematic.SpongeSchematicReader;
import org.duollectis.mapart.tools.converter.schematic.SpongeSchematicWriter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchematicFormatTest {

	@Test
	void nbtExtensionIsNbt() {
		assertThat(SchematicFormat.NBT.getExtension()).isEqualTo(".nbt");
	}

	@Test
	void litematicExtensionIsLitematic() {
		assertThat(SchematicFormat.LITEMATIC.getExtension()).isEqualTo(".litematic");
	}

	@Test
	void schemExtensionIsSchem() {
		assertThat(SchematicFormat.SCHEM.getExtension()).isEqualTo(".schem");
	}

	@Test
	void fromExtensionNbtFile() {
		assertThat(SchematicFormat.fromExtension("map_1_1.nbt")).isEqualTo(SchematicFormat.NBT);
	}

	@Test
	void fromExtensionLitematicFile() {
		assertThat(SchematicFormat.fromExtension("map_1_1.litematic")).isEqualTo(SchematicFormat.LITEMATIC);
	}

	@Test
	void fromExtensionSchemFile() {
		assertThat(SchematicFormat.fromExtension("map_1_1.schem")).isEqualTo(SchematicFormat.SCHEM);
	}

	@Test
	void fromExtensionUnknownFallsBackToNbt() {
		assertThat(SchematicFormat.fromExtension("map_1_1.schematic")).isEqualTo(SchematicFormat.NBT);
	}

	@Test
	void fromExtensionEmptyStringFallsBackToNbt() {
		assertThat(SchematicFormat.fromExtension("")).isEqualTo(SchematicFormat.NBT);
	}

	@Test
	void nbtCreateWriterReturnsNbtWriter() {
		assertThat(SchematicFormat.NBT.createWriter(128, 10, 129, "test"))
			.isInstanceOf(NbtSchematicWriter.class);
	}

	@Test
	void litematicCreateWriterReturnsLitematicWriter() {
		assertThat(SchematicFormat.LITEMATIC.createWriter(128, 10, 129, "test"))
			.isInstanceOf(LitematicSchematicWriter.class);
	}

	@Test
	void schemCreateWriterReturnsSpongeWriter() {
		assertThat(SchematicFormat.SCHEM.createWriter(128, 10, 129, "test"))
			.isInstanceOf(SpongeSchematicWriter.class);
	}

	@Test
	void nbtCreateReaderReturnsNbtReader() {
		assertThat(SchematicFormat.NBT.createReader()).isInstanceOf(NbtSchematicReader.class);
	}

	@Test
	void litematicCreateReaderReturnsLitematicReader() {
		assertThat(SchematicFormat.LITEMATIC.createReader()).isInstanceOf(LitematicSchematicReader.class);
	}

	@Test
	void schemCreateReaderReturnsSpongeReader() {
		assertThat(SchematicFormat.SCHEM.createReader()).isInstanceOf(SpongeSchematicReader.class);
	}

	@Test
	void mapDatExtensionIsDat() {
		assertThat(SchematicFormat.MAP_DAT.getExtension()).isEqualTo(".dat");
	}

	@Test
	void mapDatIsMapDat() {
		assertThat(SchematicFormat.MAP_DAT.isMapDat()).isTrue();
	}

	@Test
	void nbtIsNotMapDat() {
		assertThat(SchematicFormat.NBT.isMapDat()).isFalse();
	}

	@Test
	void fourFormatsExist() {
		assertThat(SchematicFormat.values()).hasSize(4);
	}
}
