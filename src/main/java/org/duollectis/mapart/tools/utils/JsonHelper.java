package org.duollectis.mapart.tools.utils;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.FormattingStyle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.experimental.UtilityClass;

@UtilityClass
public class JsonHelper {

	public final Gson GSON = new GsonBuilder()
		.setPrettyPrinting()
		.setFormattingStyle(FormattingStyle.PRETTY.withIndent("    "))
		.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
		.serializeNulls()
		.create();
}
