package com.hadi.test.python;

import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.test.DeclaredTypes;
import org.junit.Test;

import static com.hadi.test.python.PythonInlineProject.lines;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** The declared type of a Python method, function and parameter is the one class its annotation names. */
public class PythonDeclaredTypeTest {

    private static OOPSourceCodeModel model() throws Exception {
        return PythonInlineProject.compile("/repo.py", lines(
                "from typing import List, Optional, Union, Callable, TypeVar",
                "T = TypeVar('T')",
                "class Order: pass",
                "class Repo:",
                "    held: Order",
                "    def orders(self) -> List[Order]: return []",
                "    def find(self, id: str) -> Optional[Order]: return None",
                "    def clear(self) -> None: pass",
                "    def take(self, o: Order): pass",
                "    def inferred(self): return Order()",
                "    def either(self) -> Union[Order, int]: return 1",
                "    def callback(self) -> Callable[[int], Order]: return lambda x: Order()",
                "    def element(self, item: T) -> T: return item",
                "def make() -> Order: return Order()")).model();
    }

    @Test
    public void aFunctionAMethodAndAParameterAreDeclaredWithTheirClass() throws Exception {
        final OOPSourceCodeModel model = model();
        assertEquals("repo.Order", DeclaredTypes.of(model, "repo", "make"));
        assertEquals("repo.Order", DeclaredTypes.of(model, "repo.Repo", "find"));
        assertEquals("repo.Order", DeclaredTypes.of(model, "repo.Repo", "held"));
        assertEquals("repo.Order", DeclaredTypes.parameter(model, "repo.Repo.take(", "o").invokedComponent());
    }

    @Test
    public void aContainerOfTheClassIsNotTheClass() throws Exception {
        assertNull(DeclaredTypes.of(model(), "repo.Repo", "orders"));
    }

    @Test
    public void noneNoAnnotationUnionsFunctionsAndTypeVariablesAreNull() throws Exception {
        final OOPSourceCodeModel model = model();
        assertNull(DeclaredTypes.of(model, "repo.Repo", "clear"));
        assertNull(DeclaredTypes.of(model, "repo.Repo", "inferred"));
        assertNull(DeclaredTypes.of(model, "repo.Repo", "either"));
        assertNull(DeclaredTypes.of(model, "repo.Repo", "callback"));
        assertNull(DeclaredTypes.of(model, "repo.Repo", "element"));
    }

    @Test
    public void theDeclaredTypeIsOneOfTheReferences() throws Exception {
        DeclaredTypes.assertContainedInReferences(model());
    }
}
