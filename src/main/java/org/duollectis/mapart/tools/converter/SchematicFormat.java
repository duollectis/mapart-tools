package org.duollectis.mapart.tools.converter;

import org.duollectis.mapart.tools.converter.schematic.*;

/**
 * Формат схематики карт-арта.
 * Каждый элемент знает своё расширение файла и умеет создавать
 * соответствующий {@link SchematicWriter} (экспорт) и {@link SchematicReader} (импорт).
 */
public enum SchematicFormat {

	NBT(".nbt") {
		@Override
		public SchematicWriter createWriter(int sizeX, int sizeY, int sizeZ, String name) {
			return new NbtSchematicWriter(sizeX, sizeY, sizeZ);
		}

		@Override
		public SchematicReader createReader() {
			return new NbtSchematicReader();
		}
	},

	LITEMATIC(".litematic") {
		@Override
		public SchematicWriter createWriter(int sizeX, int sizeY, int sizeZ, String name) {
			return new LitematicSchematicWriter(sizeX, sizeY, sizeZ, name);
		}

		@Override
		public SchematicReader createReader() {
			return new LitematicSchematicReader();
		}
	},

	SCHEM(".schem") {
		@Override
		public SchematicWriter createWriter(int sizeX, int sizeY, int sizeZ, String name) {
			return new SpongeSchematicWriter(sizeX, sizeY, sizeZ, name);
		}

		@Override
		public SchematicReader createReader() {
			return new SpongeSchematicReader();
		}
	};

	private final String extension;

	SchematicFormat(String extension) {
		this.extension = extension;
	}

	public String getExtension() {
		return extension;
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
