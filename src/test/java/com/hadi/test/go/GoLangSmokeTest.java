package com.hadi.test.go;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import com.hadi.test.ClarpseTestUtil;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GoLangSmokeTest {

    private static OOPSourceCodeModel generatedSourceModel;

    @BeforeClass
    public static void setup() throws Exception {
        generatedSourceModel = ClarpseTestUtil.sourceCodeModel("/istio-master.zip", Lang.GOLANG);
    }

    @Test
    public void spotCheckStruct() {
        assertTrue(generatedSourceModel.containsComponent("tests.integration.operator" +
                                                              ".operatorDumper"));
    }

    @Test
    public void spotCheckInterface() {
        assertTrue(generatedSourceModel.containsComponent("pilot.pkg.server.Instance"));
    }

    @Test
    public void spotCheckStructImplementsInterface() {
        assertTrue(generatedSourceModel.getComponent("tests.integration.operator.operatorDumper")
                                       .get().references(TypeReferences.IMPLEMENTATION).stream().anyMatch(reference -> reference.invokedComponent()
                                                                                                                                .equals("pkg.test.framework.resource.Dumper")));
    }

    @Test
    public void spotCheckStructMethodv2() {
        assertTrue(generatedSourceModel
                       .containsComponent("istioctl.pkg.multicluster.KubeOptions.prepare(*pflag" +
                                              ".FlagSet)"));
    }

    @Test
    public void spotCheckInterfaceMethod() {
        assertTrue(generatedSourceModel.containsComponent("pilot.pkg.server.Instance.Start(<-chan" +
                                                              " struct{}) : (error)"));
    }

    @Test
    public void spotCheckStructField() {
        assertTrue(generatedSourceModel.containsComponent("tests.integration.operator" +
                                                              ".operatorDumper.ns"));
        assertTrue(generatedSourceModel.containsComponent("tests.integration.operator" +
                                                              ".operatorDumper.rev"));
    }

    @Test
    public void spotCheckStructMethod() {
        assertTrue(generatedSourceModel.containsComponent("pilot.pkg.status.Resource.String() : " +
                                                              "(string)"));
    }

    @Test
    public void spotCheckStructv2() {
        assertTrue(generatedSourceModel.containsComponent("operator.pkg.compare.YAMLCmpReporter"));
    }

    @Test
    public void spotCheckStructExternalTypeExtension() {
        assertTrue(generatedSourceModel.getComponent("pilot.pkg.status.Resource")
                                       .get().references(TypeReferences.EXTENSION).get(0).invokedComponent()
                                       .equals("k8s.io.apimachinery.pkg.runtime.schema" +
                                                   ".GroupVersionResource"));
    }

    @Test
    public void spotCheckStructInternalTypeExtension() {
        assertTrue(generatedSourceModel.getComponent("pkg.proxy.sidecarSyncStatus")
                                       .get().references(TypeReferences.EXTENSION).get(0).invokedComponent()
                                       .equals("pilot.pkg.xds.SyncStatus"));
    }

    @Test
    public void spotCheckStructcomponentReferences() {
        assertTrue(generatedSourceModel
                       .getComponent("tests.integration.operator.operatorDumper")
                       .get()
                       .references()
                       .toString()
                       .equals(
                           "[SimpleTypeReference[string], SimpleTypeReference[pkg.test.framework" +
                               ".resource.Context], TypeImplementationReference[pkg.test" +
                               ".framework.resource.Dumper], TypeImplementationReference[pkg.test" +
                               ".framework.resource.Resource], TypeImplementationReference[pkg" +
                               ".test.framework.components.opentelemetry.Instance]]"
                       )
        );
    }

    @Test
    public void spotCheckStructcomponentReferencesv2() {
        assertTrue(generatedSourceModel.getComponent("security.pkg.pki.ca.IstioCA").get().references().toString().equals(
            "[SimpleTypeReference[time.Duration], SimpleTypeReference[int], " +
                "SimpleTypeReference[security.pkg.pki.util.KeyCertBundle], " +
                "SimpleTypeReference[security.pkg.pki.ca.SelfSignedCARootCertRotator], " +
                "SimpleTypeReference[byte], SimpleTypeReference[security.pkg.pki.ca.CertOpts], " +
                "SimpleTypeReference[string], SimpleTypeReference[bool]]"));
    }

    @Test
    public void spotCheckStructPublicFieldVars() {
        assertTrue(generatedSourceModel.getComponent("security.pkg.pki.ca.CertOpts.SubjectIDs").get().modifiers().size() == 1
                       && generatedSourceModel.getComponent("security.pkg.pki.ca.CertOpts" +
                                                                ".SubjectIDs").get().modifiers()
                                              .contains("public"));
    }

    @Test
    public void spotCheckStructPrivateFieldVars() {
        assertTrue(generatedSourceModel.getComponent("pilot.pkg.controller.workloadentry" +
                                                         ".workItem.entryName").get().modifiers().size() == 1
                       && generatedSourceModel.getComponent("pilot.pkg.controller.workloadentry" +
                                                                ".workItem.entryName").get().modifiers()
                                              .contains("private"));
    }

    @Test
    public void spotCheckStructMethodParam() {
        assertTrue(generatedSourceModel.containsComponent("pkg.queue.queueImpl.pushRetryTask" +
                                                              "(*BackoffTask)"));
    }

    @Test
    public void spotCheckStructMethodParamTypeReference() {
        assertTrue(generatedSourceModel.getComponent("pkg.queue.queueImpl.pushRetryTask" +
                                                         "(*BackoffTask)").get().references(TypeReferences.SIMPLE)
                                       .get(0).invokedComponent().equals("pkg.queue.BackoffTask"));
    }

    @Test
    public void spotCheckStructMethodLocalVar() {
        assertTrue(generatedSourceModel.containsComponent("pkg.queue.delayQueue.Run(<-chan " +
                                                              "struct{}).task"));
    }

    @Test
    public void spotCheckStructMethodLocalVarReference() {
        assertTrue(generatedSourceModel.getComponent("pkg.queue.delayQueue.Run(<-chan struct{})" +
                                                         ".task").get().references(TypeReferences.SIMPLE)
                                       .get(0).invokedComponent().equals("pkg.queue.delayTask"));
    }

    @Test
    public void spotCheckStructPrivateStruct() {
        assertTrue(generatedSourceModel.getComponent("pilot.pkg.controller.workloadentry" +
                                                         ".workItem").get().modifiers().size() == 1
                       && generatedSourceModel.getComponent("pilot.pkg.controller.workloadentry" +
                                                                ".workItem").get().modifiers()
                                              .contains("private"));
    }

    @Test
    public void spotCheckInterfaceImports() {
        assertTrue(generatedSourceModel.getComponent("pkg.kube.inject.Injector").get().imports().toString()
                                       .equals("[bufio, sigs.k8s.io.yaml, k8s.io.apimachinery.pkg" +
                                                   ".api.resource, fmt, istio.io.pkg.log, istio" +
                                                   ".io.api.mesh.v1alpha1, github.com.Masterminds" +
                                                   ".sprig.v3, reflect, strings, k8s.io.api.core" +
                                                   ".v1, k8s.io.apimachinery.pkg.runtime, pkg" +
                                                   ".config.mesh, k8s.io.apimachinery.pkg.runtime" +
                                                   ".schema, k8s.io.api.batch.v1, operator.pkg" +
                                                   ".apis.istio.v1alpha1, io, istio.io.api" +
                                                   ".annotation, github.com.evanphx.json-patch" +
                                                   ".v5, k8s.io.apimachinery.pkg.apis.meta.v1, " +
                                                   "istio.io.api.networking.v1beta1, sort, github" +
                                                   ".com.hashicorp.go-multierror, text.template, " +
                                                   "encoding.json, k8s.io.apimachinery.pkg.util" +
                                                   ".yaml, k8s.io.apimachinery.pkg.labels, bytes," +
                                                   " strconv, k8s.io.api.apps.v1, pkg.util" +
                                                   ".gogoprotomarshal, math, istio.io.api.label]"));
    }

    @Test
    public void spotCheckStructDoc() {
        assertTrue(generatedSourceModel.getComponent("pilot.pkg.controller.workloadentry" +
                                                         ".workItem").get().comment().trim()
                                       .equals("workItem contains the state of a \"disconnect\" " +
                                                   "event used to unregister a workload."));
    }

    @Test
    public void spotCheckStructFieldDoc() {
        assertTrue(generatedSourceModel.getComponent("pilot.pkg.controller.workloadentry" +
                                                         ".Controller.queue").get().comment().trim()
                                       .equals("Note: unregister is to update the workload entry " +
                                                   "status: like setting " +
                                                   "`DisconnectedAtAnnotation` and make the " +
                                                   "workload entry enqueue `cleanupQueue` cleanup" +
                                                   " is to delete the workload entry queue " +
                                                   "contains workloadEntry that need to be " +
                                                   "unregistered"));
    }

    @Test
    public void spotCheckStructFuncDoc() {
        assertTrue(generatedSourceModel.getComponent("pilot.pkg.controller.workloadentry" +
                                                         ".Controller.QueueWorkloadEntryHealth" +
                                                         "(*model.Proxy, HealthEvent)").get().comment()
                                       .trim().equals("QueueWorkloadEntryHealth enqueues the " +
                                                          "associated WorkloadEntries health " +
                                                          "status."));
    }
}