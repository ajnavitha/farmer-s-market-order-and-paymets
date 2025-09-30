package assignment2;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class FarmersMarket {

    // --- Storage (in-memory) ---
    private final Map<Integer, Vendor> vendors = new HashMap<>();
    private final Map<Integer, Product> products = new HashMap<>();
    private final Map<Integer, Order> orders = new HashMap<>();
    private final Map<Integer, Payment> payments = new HashMap<>();
    private final Map<Integer, Delivery> deliveries = new HashMap<>();
    private final Map<Integer, ReturnRequest> returns = new HashMap<>();

    private final Scanner scanner = new Scanner(System.in);

    // ID generators
    private final AtomicInteger vendorIdGen = new AtomicInteger(1000);
    private final AtomicInteger productIdGen = new AtomicInteger(2000);
    private final AtomicInteger orderIdGen = new AtomicInteger(3000);
    private final AtomicInteger paymentIdGen = new AtomicInteger(4000);
    private final AtomicInteger deliveryIdGen = new AtomicInteger(5000);
    private final AtomicInteger returnIdGen = new AtomicInteger(6000);

    public static void main(String[] args) {
        FarmersMarket app = new FarmersMarket();
        app.seedDemoData(); // optional demo data
        app.runMenu();
    }

    private void runMenu() {
        while (true) {
            System.out.println("\n=== Farmers' Market Menu ===");
            System.out.println("1. Add Vendor");
            System.out.println("2. Add Product");
            System.out.println("3. Place Order");
            System.out.println("4. Record Payment");
            System.out.println("5. Schedule Delivery");
            System.out.println("6. Request Return");
            System.out.println("7. Display Inventory");
            System.out.println("8. Display Orders");
            System.out.println("9. Exit");
            System.out.print("Choose an option: ");

            int choice = readIntInRange(1, 9);
            switch (choice) {
                case 1 -> addVendor();
                case 2 -> addProduct();
                case 3 -> placeOrder();
                case 4 -> recordPayment();
                case 5 -> scheduleDelivery();
                case 6 -> requestReturn();
                case 7 -> displayInventory();
                case 8 -> displayOrders();
                case 9 -> {
                    System.out.println("Exiting. Goodbye!");
                    return;
                }
            }
        }
    }

    private void addVendor() {
        System.out.println("\n-- Add Vendor --");
        System.out.print("Vendor name: ");
        String name = readNonEmptyString();
        int id = vendorIdGen.getAndIncrement();
        Vendor v = new Vendor(id, name);
        vendors.put(id, v);
        System.out.println("Added vendor: " + v);
    }

    private void addProduct() {
        System.out.println("\n-- Add Product --");
        if (vendors.isEmpty()) {
            System.out.println("No vendors available. Add a vendor first.");
            return;
        }
        System.out.println("Available vendors:");
        vendors.values().forEach(System.out::println);
        System.out.print("Vendor ID to add product to: ");
        int vendorId = readInt();
        Vendor vendor = vendors.get(vendorId);
        if (vendor == null) {
            System.out.println("Invalid vendor ID.");
            return;
        }
        System.out.print("Product name: ");
        String pname = readNonEmptyString();
        System.out.print("Unit price (positive number): ");
        double price = readPositiveDouble();
        System.out.print("Initial stock (integer >=0): ");
        int stock = readNonNegativeInt();

        int pid = productIdGen.getAndIncrement();
        Product p = new Product(pid, pname, price, vendorId);
        products.put(pid, p);
        vendor.addStock(pid, stock);
        System.out.println("Added product: " + p + " with stock " + stock + " for vendor " + vendor.getName());
    }

    private void placeOrder() {
        System.out.println("\n-- Place Order --");
        if (vendors.isEmpty() || products.isEmpty()) {
            System.out.println("Need at least one vendor and one product to place orders.");
            return;
        }
        System.out.println("Vendors:");
        vendors.values().forEach(System.out::println);
        System.out.print("Choose Vendor ID to order from: ");
        int vendorId = readInt();
        Vendor vendor = vendors.get(vendorId);
        if (vendor == null) {
            System.out.println("Invalid vendor.");
            return;
        }
        // List vendor's products
        List<Product> vendorProducts = new ArrayList<>();
        for (Product p : products.values()) {
            if (p.getVendorId() == vendorId) vendorProducts.add(p);
        }
        if (vendorProducts.isEmpty()) {
            System.out.println("This vendor has no products.");
            return;
        }
        System.out.println("Products for vendor " + vendor.getName() + ":");
        vendorProducts.forEach(p -> System.out.println(p + " | Stock: " + vendor.getStockForProduct(p.getId())));

        List<OrderItem> items = new ArrayList<>();
        while (true) {
            System.out.print("Enter Product ID to add (or 0 to finish): ");
            int pid = readInt();
            if (pid == 0) break;
            Product prod = products.get(pid);
            if (prod == null || prod.getVendorId() != vendorId) {
                System.out.println("Product not found for this vendor.");
                continue;
            }
            System.out.print("Enter quantity: ");
            int qty = readPositiveInt();
            int avail = vendor.getStockForProduct(pid);
            if (qty > avail) {
                System.out.println("Requested quantity exceeds available stock (" + avail + ").");
                continue;
            }
            items.add(new OrderItem(pid, prod.getName(), qty, prod.getPrice()));
            System.out.println("Added " + qty + " x " + prod.getName());
        }
        if (items.isEmpty()) {
            System.out.println("No items selected. Cancelling order.");
            return;
        }
        int oid = orderIdGen.getAndIncrement();
        Order order = new Order(oid, vendorId, items);
        orders.put(oid, order);
        // Confirm order: decrease stock immediately per business rule (on order confirmation)
        order.setStatus(Order.Status.CONFIRMED);
        for (OrderItem it : items) {
            vendor.decreaseStock(it.getProductId(), it.getQuantity());
        }
        System.out.println("Order placed and confirmed. Order ID: " + oid + ". Total: " + String.format("%.2f", order.getTotalAmount()));
        System.out.println("Note: Payment must be recorded before scheduling delivery.");
    }

    private void recordPayment() {
        System.out.println("\n-- Record Payment --");
        System.out.println("Pending/Confirmed orders:");
        orders.values().stream().filter(o -> o.getStatus() == Order.Status.CONFIRMED || o.getStatus() == Order.Status.PAYMENT_PENDING)
                .forEach(System.out::println);
        System.out.print("Enter Order ID to record payment for: ");
        int oid = readInt();
        Order order = orders.get(oid);
        if (order == null) {
            System.out.println("Order not found.");
            return;
        }
        if (order.getStatus() == Order.Status.DELIVERED) {
            System.out.println("Order already delivered; no payment required.");
            return;
        }
        double amountDue = order.getTotalAmount() - order.getPaidAmount();
        if (amountDue <= 0.0) {
            System.out.println("Order already fully paid.");
            return;
        }
        System.out.println("Amount due: " + String.format("%.2f", amountDue));
        System.out.print("Enter payment amount: ");
        double paid = readPositiveDouble();
        if (paid > amountDue) {
            System.out.println("Payment exceeds amount due. Will record up to due amount and return change to customer.");
            paid = amountDue;
        }
        System.out.print("Payment method (cash/upi/card): ");
        String method = readNonEmptyString();
        int pid = paymentIdGen.getAndIncrement();
        Payment pay = new Payment(pid, oid, paid, method);
        payments.put(pid, pay);
        order.addPayment(pay);
        System.out.println("Payment recorded: " + pay);
        if (order.getPaidAmount() >= order.getTotalAmount()) {
            order.setStatus(Order.Status.PAID);
            System.out.println("Order fully paid.");
        } else {
            order.setStatus(Order.Status.PAYMENT_PENDING);
            System.out.println("Order partially paid. Remaining: " + String.format("%.2f", order.getTotalAmount() - order.getPaidAmount()));
        }
    }

    private void scheduleDelivery() {
        System.out.println("\n-- Schedule Delivery --");
        System.out.println("Orders ready for delivery (PAID):");
        orders.values().stream().filter(o -> o.getStatus() == Order.Status.PAID)
                .forEach(System.out::println);
        System.out.print("Enter Order ID to schedule delivery for: ");
        int oid = readInt();
        Order order = orders.get(oid);
        if (order == null) { System.out.println("Order not found."); return; }
        if (order.getStatus() != Order.Status.PAID) {
            System.out.println("Payments must be settled before scheduling delivery.");
            return;
        }
        System.out.print("Enter delivery date (YYYY-MM-DD) or 'today': ");
        String date = readNonEmptyString();
        int did = deliveryIdGen.getAndIncrement();
        Delivery d = new Delivery(did, oid, date);
        deliveries.put(did, d);
        order.setStatus(Order.Status.SCHEDULED_FOR_DELIVERY);
        System.out.println("Delivery scheduled: " + d);
    }

    private void requestReturn() {
        System.out.println("\n-- Request Return --");
        System.out.println("Delivered orders:");
        orders.values().stream().filter(o -> o.getStatus() == Order.Status.DELIVERED || o.getStatus() == Order.Status.SCHEDULED_FOR_DELIVERY)
                .forEach(System.out::println);
        System.out.print("Enter Order ID to request return for: ");
        int oid = readInt();
        Order order = orders.get(oid);
        if (order == null) { System.out.println("Order not found."); return; }
        System.out.println("Order items:");
        order.getItems().forEach(System.out::println);
        System.out.print("Enter Product ID from this order to return: ");
        int pid = readInt();
        Optional<OrderItem> opt = order.getItems().stream().filter(i -> i.getProductId() == pid).findFirst();
        if (opt.isEmpty()) { System.out.println("Product not in order."); return; }
        OrderItem oitem = opt.get();
        System.out.print("Enter quantity to return (max " + oitem.getQuantity() + "): ");
        int qty = readPositiveInt();
        if (qty > oitem.getQuantity()) { System.out.println("Cannot return more than purchased."); return; }
        // Create return request (will be "PENDING_APPROVAL")
        int rid = returnIdGen.getAndIncrement();
        ReturnRequest rr = new ReturnRequest(rid, oid, pid, qty);
        returns.put(rid, rr);
        System.out.println("Return request created with ID " + rid + ". Admin must approve to restock.");
        // For this simple app we'll ask admin to approve immediately for demo
        System.out.print("Approve return now? (y/n): ");
        String ans = scanner.nextLine().trim().toLowerCase();
        if (ans.equals("y") || ans.equals("yes")) {
            approveReturn(rr);
        } else {
            System.out.println("Return kept pending.");
        }
    }

    private void approveReturn(ReturnRequest rr) {
        if (rr.getStatus() != ReturnRequest.Status.PENDING_APPROVAL) {
            System.out.println("Return not pending.");
            return;
        }
        // Update order and vendor stock
        Order order = orders.get(rr.getOrderId());
        if (order == null) { System.out.println("Order missing; cannot approve."); return; }
        Product p = products.get(rr.getProductId());
        if (p == null) { System.out.println("Product missing; cannot approve."); return; }
        Vendor v = vendors.get(p.getVendorId());
        if (v == null) { System.out.println("Vendor missing; cannot approve."); return; }
        // Increase vendor stock
        v.increaseStock(p.getId(), rr.getQuantity());
        rr.setStatus(ReturnRequest.Status.APPROVED);
        // Optionally process refund: simple instantaneous refund to order payments
        double refundAmount = rr.getQuantity() * p.getPrice();
        order.addRefund(refundAmount);
        order.decreasePurchasedQuantityForProduct(p.getId(), rr.getQuantity());
        System.out.println("Return approved. Restocked " + rr.getQuantity() + " x " + p.getName() + ". Refund: " + String.format("%.2f", refundAmount));
    }

    private void displayInventory() {
        System.out.println("\n-- Display Inventory --");
        if (vendors.isEmpty()) { System.out.println("No vendors."); return; }
        for (Vendor v : vendors.values()) {
            System.out.println(v);
            System.out.println("Products:");
            for (Integer pid : v.getInventory().keySet()) {
                Product p = products.get(pid);
                if (p == null) continue;
                System.out.println("  " + p + " | Stock: " + v.getStockForProduct(pid));
            }
            System.out.println();
        }
    }

    private void displayOrders() {
        System.out.println("\n-- Orders --");
        if (orders.isEmpty()) { System.out.println("No orders."); return; }
        orders.values().forEach(System.out::println);
    }

    // --- Utilities & validators ---
    private int readInt() {
        while (true) {
            String s = scanner.nextLine().trim();
            try { return Integer.parseInt(s); } catch (Exception e) { System.out.print("Enter a valid integer: "); }
        }
    }

    private int readIntInRange(int low, int high) {
        while (true) {
            int v = readInt();
            if (v >= low && v <= high) return v;
            System.out.print("Enter a number between " + low + " and " + high + ": ");
        }
    }

    private int readPositiveInt() {
        while (true) {
            int v = readInt();
            if (v > 0) return v;
            System.out.print("Enter a positive integer: ");
        }
    }

    private int readNonNegativeInt() {
        while (true) {
            int v = readInt();
            if (v >= 0) return v;
            System.out.print("Enter a non-negative integer: ");
        }
    }

    private double readPositiveDouble() {
        while (true) {
            String s = scanner.nextLine().trim();
            try {
                double d = Double.parseDouble(s);
                if (d > 0) return d;
            } catch (Exception e) { /* fallthrough */ }
            System.out.print("Enter a positive number: ");
        }
    }

    private String readNonEmptyString() {
        while (true) {
            String s = scanner.nextLine().trim();
            if (!s.isEmpty()) return s;
            System.out.print("Input cannot be empty. Try again: ");
        }
    }

    // Seed demo data for convenience
    private void seedDemoData() {
        Vendor v1 = new Vendor(vendorIdGen.getAndIncrement(), "Green Valley Produce");
        Vendor v2 = new Vendor(vendorIdGen.getAndIncrement(), "Sunny Farms");
        vendors.put(v1.getId(), v1);
        vendors.put(v2.getId(), v2);
        Product p1 = new Product(productIdGen.getAndIncrement(), "Tomato", 30.0, v1.getId());
        Product p2 = new Product(productIdGen.getAndIncrement(), "Potato", 20.0, v1.getId());
        Product p3 = new Product(productIdGen.getAndIncrement(), "Carrot", 25.0, v2.getId());
        products.put(p1.getId(), p1);
        products.put(p2.getId(), p2);
        products.put(p3.getId(), p3);
        v1.addStock(p1.getId(), 100);
        v1.addStock(p2.getId(), 200);
        v2.addStock(p3.getId(), 150);
    }

}

// ---------------------- Domain classes ----------------------
class Vendor {
    private final int id;
    private final String name;
    // productId -> stock
    private final Map<Integer, Integer> inventory = new HashMap<>();

    public Vendor(int id, String name) { this.id = id; this.name = name; }

    public int getId() { return id; }
    public String getName() { return name; }

    public void addStock(int productId, int qty) {
        inventory.put(productId, inventory.getOrDefault(productId, 0) + qty);
    }

    public void decreaseStock(int productId, int qty) {
        int cur = inventory.getOrDefault(productId, 0);
        if (qty <= 0) return;
        if (qty > cur) throw new IllegalArgumentException("Insufficient stock");
        inventory.put(productId, cur - qty);
    }

    public void increaseStock(int productId, int qty) {
        addStock(productId, qty);
    }

    public int getStockForProduct(int productId) { return inventory.getOrDefault(productId, 0); }

    public Map<Integer, Integer> getInventory() { return inventory; }

    @Override
    public String toString() { return "Vendor[" + id + "] " + name; }
}

class Product {
    private final int id;
    private final String name;
    private final double price;
    private final int vendorId;

    public Product(int id, String name, double price, int vendorId) {
        this.id = id; this.name = name; this.price = price; this.vendorId = vendorId;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public double getPrice() { return price; }
    public int getVendorId() { return vendorId; }

    @Override
    public String toString() { return "Product[" + id + "] " + name + " (" + String.format("%.2f", price) + ")"; }
}

class Order {
    public enum Status {CREATED, CONFIRMED, PAYMENT_PENDING, PAID, SCHEDULED_FOR_DELIVERY, DELIVERED, CANCELLED}
    private final int id;
    private final int vendorId;
    private final List<OrderItem> items = new ArrayList<>();
    private Status status;
    private final List<Payment> paymentHistory = new ArrayList<>();
    private double refundedAmount = 0.0;

    public Order(int id, int vendorId, List<OrderItem> items) {
        this.id = id; this.vendorId = vendorId; this.items.addAll(items); this.status = Status.CREATED;
    }

    public int getId() { return id; }
    public int getVendorId() { return vendorId; }
    public List<OrderItem> getItems() { return items; }
    public Status getStatus() { return status; }
    public void setStatus(Status s) { this.status = s; }

    public double getTotalAmount() {
        double t = 0.0; for (OrderItem i : items) t += i.getQuantity() * i.getUnitPrice(); return t;
    }

    public void addPayment(Payment p) { paymentHistory.add(p); }
    public double getPaidAmount() { return paymentHistory.stream().mapToDouble(Payment::getAmount).sum(); }

    public void addRefund(double amt) { this.refundedAmount += amt; }

    public void decreasePurchasedQuantityForProduct(int productId, int qty) {
        for (OrderItem i : items) {
            if (i.getProductId() == productId) {
                i.decreaseQuantity(qty);
                break;
            }
        }
    }

    @Override
    public String toString() {
        return "Order[" + id + "] Vendor=" + vendorId + " Status=" + status + " Total=" + String.format("%.2f", getTotalAmount()) + " Paid=" + String.format("%.2f", getPaidAmount()) + " Items=" + items;
    }
}

class OrderItem {
    private final int productId;
    private final String productName;
    private int quantity;
    private final double unitPrice;

    public OrderItem(int productId, String productName, int quantity, double unitPrice) {
        this.productId = productId; this.productName = productName; this.quantity = quantity; this.unitPrice = unitPrice;
    }

    public int getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public double getUnitPrice() { return unitPrice; }

    public void decreaseQuantity(int q) {
        if (q <= 0) return;
        this.quantity = Math.max(0, this.quantity - q);
    }

    @Override
    public String toString() { return "{" + productName + "[" + productId + "] x" + quantity + " @" + String.format("%.2f", unitPrice) + "}"; }
}

class Payment {
    public enum Status {RECEIVED}
    private final int id;
    private final int orderId;
    private final double amount;
    private final String method;
    private final Date timestamp;

    public Payment(int id, int orderId, double amount, String method) {
        this.id = id; this.orderId = orderId; this.amount = amount; this.method = method; this.timestamp = new Date();
    }

    public int getId() { return id; }
    public int getOrderId() { return orderId; }
    public double getAmount() { return amount; }

    @Override
    public String toString() { return "Payment[" + id + "] Order=" + orderId + " Amt=" + String.format("%.2f", amount) + " Method=" + method; }
}

class Delivery {
    public enum Status {SCHEDULED, OUT_FOR_DELIVERY, DELIVERED}
    private final int id;
    private final int orderId;
    private final String scheduledDate; // simple string for console app
    private Status status;

    public Delivery(int id, int orderId, String scheduledDate) { this.id = id; this.orderId = orderId; this.scheduledDate = scheduledDate; this.status = Status.SCHEDULED; }

    @Override
    public String toString() { return "Delivery[" + id + "] Order=" + orderId + " Date=" + scheduledDate + " Status=" + status; }
}

class ReturnRequest {
    public enum Status {PENDING_APPROVAL, APPROVED, DENIED}
    private final int id;
    private final int orderId;
    private final int productId;
    private final int quantity;
    private Status status;

    public ReturnRequest(int id, int orderId, int productId, int quantity) {
        this.id = id; this.orderId = orderId; this.productId = productId; this.quantity = quantity; this.status = Status.PENDING_APPROVAL;
    }

    public int getId() { return id; }
    public int getOrderId() { return orderId; }
    public int getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public Status getStatus() { return status; }
    public void setStatus(Status s) { this.status = s; }

    @Override
    public String toString() { return "Return[" + id + "] Order=" + orderId + " Product=" + productId + " Qty=" + quantity + " Status=" + status; }
}

