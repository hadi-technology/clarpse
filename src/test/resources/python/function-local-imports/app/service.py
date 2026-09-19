from app.models import Invoice


class Service:
    def make_order(self):
        from app.models import Order
        return Order()

    def make_invoice(self):
        return Invoice()

    def shadowing_import(self):
        from app.other import Invoice
        return Invoice()

    def order_without_import(self):
        return Order()

    def relative_import(self):
        from .models import Refund
        return Refund()

    def inside_nested_blocks(self, flag):
        if flag:
            try:
                from app.models import Receipt
                return Receipt()
            except ImportError:
                return None
        return None

    def module_import(self):
        import app.models as m
        return m.Ledger()

    def annotated_local(self):
        from app.models import Order
        order: Order = Order()
        return order

    def rebound_import(self, fallback):
        from app.models import Ledger
        if fallback:
            Ledger = fallback
        return Ledger()

    def import_in_nested_function(self):
        def build():
            from app.models import Receipt
            return Receipt()
        return build


def build_order():
    from app.models import Order
    return Order()
