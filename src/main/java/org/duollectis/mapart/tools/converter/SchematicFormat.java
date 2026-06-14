package org.duollectis.mapart.tools.converter;

import org.duollectis.mapart.tools.converter.schematic.*;
import org.duollectis.mapart.tools.gui.util.UpdatableRegistry;
import org.duollectis.mapart.tools.gui.widget.HasDescription;

/**
 * Формат экспорта карт-арта.
 * Схематичные форматы (NBT, LITEMATIC, SCHEM) создают {@link SchematicWriter}/{@link SchematicReader}.
 * Формат MAP_DAT использует {@link MapDatWriter} и не поддерживает импорт.
 */
public enum SchematicFormat implements HasDescription {

	NBT(".nbt", "format.NBT") {
		@Override
		public SchematicWriter createWriter(int sizeX, int sizeY, int sizeZ, String name) {
			return new NbtSchematicWriter(sizeX, sizeY, sizeZ);
		}

		@Override
		public SchematicReader createReader() {
			return new NbtSchematicReader();
		}
	},

	LITEMATIC(".litematic", "format.LITEMATIC") {
		@Override
		public SchematicWriter createWriter(int sizeX, int sizeY, int sizeZ, String name) {
			return new LitematicSchematicWriter(sizeX, sizeY, sizeZ, name);
		}

		@Override
		public SchematicReader createReader() {
			return new LitematicSchematicReader();
		}
	},

	SCHEM(".schem", "format.SCHEM") {
		@Override
		public SchematicWriter createWriter(int sizeX, int sizeY, int sizeZ, String name) {
			return new SpongeSchematicWriter(sizeX, sizeY, sizeZ, name);
		}

		@Override
		public SchematicReader createReader() {
			return new SpongeSchematicReader();
		}
	},

	MAP_DAT(".dat", "format.MAP_DAT") {
		@Override
		public SchematicWriter createWriter(int sizeX, int sizeY, int sizeZ, String name) {
			throw new UnsupportedOperationException("MAP_DAT не использует SchematicWriter");
		}

		@Override
		public SchematicReader createReader() {
			throw new UnsupportedOperationException("MAP_DAT не поддерживает импорт");
		}
	};

	private final String extension;
	private final String langKey;

	SchematicFormat(String extension, String langKey) {
		this.extension = extension;
		this.langKey = langKey;
	}

	public String getExtension() {
		return extension;
	}

	public boolean isMapDat() {
		return this == MAP_DAT;
	}

	@Override
	public String getDescription() {
		return UpdatableRegistry.translate(langKey + ".desc");
	}

	public abstract SchematicWriter createWriter(int sizeX, int sizeY, int sizeZ, String name);

	public abstract SchematicReader createReader();

	public static SchematicFormat fromExtension(String filename) {
		for (SchematicFormat format : values()) {
			if (filename.endsWith(format.extension)) {
				return format;
			}
		}

		return NBT;
	}
}
