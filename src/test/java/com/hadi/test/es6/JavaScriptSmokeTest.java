package com.hadi.test.es6;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.apache.commons.io.IOUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.Assert.assertTrue;

public class JavaScriptSmokeTest {

	private static OOPSourceCodeModel generatedSourceModel;

	@BeforeClass
	public static void setup() throws Exception {
		String code = IOUtils.toString(Objects.requireNonNull(
				JavaScriptSmokeTest.class.getResourceAsStream(
						"/sample-es6.txt")), StandardCharsets.UTF_8);
		final ProjectFiles rawData = new ProjectFiles();
		rawData.insertFile(new ProjectFile("/polygon.js", code));
		final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
		generatedSourceModel = parseService.result().model();
	}

	@Test
	public void TestAllClassessWereParsed() {
		assertTrue(generatedSourceModel.containsComponent("Square"));
		assertTrue(generatedSourceModel.containsComponent("Rectangle"));
		assertTrue(generatedSourceModel.containsComponent("Polygon"));
		assertTrue(generatedSourceModel.containsComponent("MyPoly"));
		assertTrue(generatedSourceModel.containsComponent("Triple"));
		assertTrue(generatedSourceModel.containsComponent("BiggerTriple"));
		assertTrue(generatedSourceModel.containsComponent("MyDate"));
		assertTrue(generatedSourceModel.containsComponent("ExtendedUint8Array"));
	}

	@Test
	public void TestAllMethodsWereParsed() {
		assertTrue(generatedSourceModel.containsComponent("Square.set_area"));
		assertTrue(generatedSourceModel.containsComponent("Square.get_area"));
		assertTrue(generatedSourceModel.containsComponent("Rectangle.sayName"));
		assertTrue(generatedSourceModel.containsComponent("Triple.triple"));
		assertTrue(generatedSourceModel.containsComponent("BiggerTriple.triple"));
		assertTrue(generatedSourceModel.containsComponent("Polygon.constructor"));
		assertTrue(generatedSourceModel.containsComponent("Polygon.sayName"));
		assertTrue(generatedSourceModel.containsComponent("Polygon.sayHistory"));
		assertTrue(generatedSourceModel.containsComponent("MyPoly.getPolyName"));
		assertTrue(generatedSourceModel.containsComponent("ExtendedUint8Array.constructor"));
		assertTrue(generatedSourceModel.containsComponent("Square.constructor"));
		assertTrue(generatedSourceModel.containsComponent("Rectangle.constructor"));
		assertTrue(generatedSourceModel.containsComponent("MyDate.constructor"));
		assertTrue(generatedSourceModel.containsComponent("MyDate.getFormattedDate"));
	}

	@Test
	public void testSquareExtendsPolygon() {
		assertTrue(generatedSourceModel.getComponent("Square").get().references(TypeReferences.EXTENSION)
				.get(0).invokedComponent().equals("Polygon"));
	}

	@Test
	public void testExtendedUint8ArrayComment() {
		assertTrue(generatedSourceModel.getComponent(
			"ExtendedUint8Array").hashCode() == -899427564);
	}

	@Test
	public void testStaticMethodWasParsed() {
		assertTrue(generatedSourceModel.getComponent("Triple.triple").get().modifiers().iterator().next().equals("static"));
	}
}