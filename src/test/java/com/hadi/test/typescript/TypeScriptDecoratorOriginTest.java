package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.reference.AnnotationReference;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A decorator an import binds from a package is named the way a call of an imported function is:
 * {@code package:name}, by the name the package exports. A decorator of the project keeps its
 * declaration as target, and a decorator the file does not import keeps the name as written.
 */
public class TypeScriptDecoratorOriginTest {

    private static final String CONFIG = "{\"compilerOptions\":{\"target\":\"es2020\","
            + "\"experimentalDecorators\":true},\"include\":[\"src\"]}";

    @Before
    public void nodeIsAvailable() {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
    }

    private static CompileResult compile(final String source) throws Exception {
        return TypeScriptReferenceGapsTest.compileWithConfig(CONFIG, "/src/a.ts", source,
                "/src/ent.ts", "export function Local(): any { return () => {}; }\n"
                        + "export function Mark(target: any): void {}\n");
    }

    /** The annotation references of the named component, as {@code target resolution}. */
    private static List<String> decorators(final CompileResult result, final String name) {
        final Component component = result.model().components()
                .filter(c -> name.equals(c.name())).findFirst()
                .orElseThrow(() -> new AssertionError("no component " + name));
        return component.references().stream()
                .filter(r -> r instanceof AnnotationReference)
                .map(r -> r.invokedComponent() + " " + r.resolutionKind())
                .collect(Collectors.toList());
    }

    @Test
    public void aNamedImportIsNamedByItsPackage() throws Exception {
        final CompileResult result = compile("""
                import { Module, Controller, Get, UseGuards, Inject } from "@nestjs/common";
                import { InjectRepository } from "@nestjs/typeorm";
                @Module({}) export class M {}
                @Controller("x")
                @UseGuards(Object)
                export class C {
                  constructor(@InjectRepository(Object) private users: any, @Inject("T") private t: string) {}
                  @Get() list() { return 1; }
                }
                """);
        Assert.assertEquals(List.of("@nestjs/common:Module EXACT"), decorators(result, "M"));
        Assert.assertEquals(List.of("@nestjs/common:Controller EXACT", "@nestjs/common:UseGuards EXACT"),
                sorted(decorators(result, "C")));
        Assert.assertEquals(List.of("@nestjs/typeorm:InjectRepository EXACT"), decorators(result, "users"));
        Assert.assertEquals(List.of("@nestjs/common:Inject EXACT"), decorators(result, "t"));
        Assert.assertEquals(List.of("@nestjs/common:Get EXACT"), decorators(result, "list"));
    }

    @Test
    public void anImportUnderAnotherLocalNameIsNamedByTheImportedName() throws Exception {
        final CompileResult result = compile("""
                import { Get as G } from "@nestjs/common";
                export class C { @G() list() { return 1; } }
                """);
        Assert.assertEquals(List.of("@nestjs/common:Get EXACT"), decorators(result, "list"));
    }

    @Test
    public void aNamespaceImportIsNamedByItsPackage() throws Exception {
        final CompileResult result = compile("""
                import * as ng from "@angular/core";
                @ng.Component({}) export class W {}
                """);
        Assert.assertEquals(List.of("@angular/core:Component EXACT"), decorators(result, "W"));
    }

    @Test
    public void aDefaultImportIsNamedByItsPackage() throws Exception {
        final CompileResult result = compile("""
                import Injectable from "inversify";
                @Injectable() export class W {}
                """);
        Assert.assertEquals(List.of("inversify:Injectable EXACT"), decorators(result, "W"));
    }

    @Test
    public void aDecoratorOfTheProjectKeepsItsDeclaration() throws Exception {
        final CompileResult result = compile("""
                import { Local } from "./ent";
                @Local() export class W {}
                """);
        final List<String> found = decorators(result, "W");
        Assert.assertEquals(found.toString(), 1, found.size());
        Assert.assertTrue(found.toString(), found.get(0).startsWith("src.ent.Local "));
        Assert.assertFalse(found.toString(), found.get(0).contains(":"));
    }

    @Test
    public void aLocalFunctionUsedAsADecoratorIsNotAPackage() throws Exception {
        final CompileResult result = compile("""
                function mark(target: any): void {}
                @mark export class W {}
                """);
        final List<String> found = decorators(result, "W");
        Assert.assertEquals(found.toString(), 1, found.size());
        Assert.assertFalse(found.toString(), found.get(0).contains(":"));
    }

    @Test
    public void aDecoratorTheFileDoesNotImportStaysAsWritten() throws Exception {
        final CompileResult result = compile("""
                declare function Unknown(): any;
                @Unknown() export class W {}
                @Missing() export class V {}
                """);
        Assert.assertTrue(decorators(result, "V").toString(),
                decorators(result, "V").equals(List.of("Missing UNRESOLVED")));
    }

    @Test
    public void twoReferencesToTheSameDecoratorAreEqual() throws Exception {
        final CompileResult result = compile("""
                import { Get } from "@nestjs/common";
                export class C { @Get() a() { return 1; } @Get() b() { return 2; } }
                """);
        final ComponentReference first = annotation(result, "a");
        final ComponentReference second = annotation(result, "b");
        Assert.assertEquals(first, second);
        Assert.assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void argumentsAreUnchanged() throws Exception {
        final CompileResult result = compile("""
                import { Controller } from "@nestjs/common";
                @Controller("x") export class C {}
                """);
        final AnnotationReference reference = (AnnotationReference) annotation(result, "C");
        Assert.assertEquals(1, reference.arguments().size());
        Assert.assertEquals("x", reference.arguments().values().iterator().next().value());
    }

    private static ComponentReference annotation(final CompileResult result, final String name) {
        return result.model().components().filter(c -> name.equals(c.name())).findFirst().orElseThrow()
                .references().stream().filter(r -> r instanceof AnnotationReference).findFirst().orElseThrow();
    }

    private static List<String> sorted(final List<String> values) {
        return values.stream().sorted().collect(Collectors.toList());
    }
}
