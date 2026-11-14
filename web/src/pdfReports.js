import { jsPDF } from "jspdf";
import autoTable from "jspdf-autotable";

// The standard PDF fonts cannot print the ₹ symbol, so the PDF uses "Rs."
const money = (n) => "Rs. " + Number(n || 0).toLocaleString("en-IN");
const ACCENT = [255, 77, 28];

function fmtDate(s) {
  if (!s) return "-";
  const [y, m, d] = String(s).split("-").map(Number);
  return new Date(y, m - 1, d).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
}

function header(doc, title, subtitle) {
  doc.setFontSize(18);
  doc.setTextColor(...ACCENT);
  doc.text("BxoStock", 14, 16);
  doc.setFontSize(14);
  doc.setTextColor(30);
  doc.text(title, 14, 25);
  doc.setFontSize(10);
  doc.setTextColor(110);
  doc.text(subtitle, 14, 31);
  doc.text("Generated: " + new Date().toLocaleString("en-IN"), 14, 36);
}

function summary(doc, lines, y) {
  doc.setFontSize(11);
  doc.setTextColor(30);
  lines.forEach(([label, value], i) => {
    doc.text(label + ":", 14, y + i * 7);
    doc.text(String(value), 70, y + i * 7);
  });
  return y + lines.length * 7;
}

function footer(doc) {
  const pages = doc.getNumberOfPages();
  const w = doc.internal.pageSize.getWidth();
  const h = doc.internal.pageSize.getHeight();
  for (let i = 1; i <= pages; i++) {
    doc.setPage(i);
    doc.setFontSize(9);
    doc.setTextColor(140);
    doc.text(`Page ${i} of ${pages}`, w - 14, h - 8, { align: "right" });
  }
}

export function salesPdf(data, from, to) {
  const doc = new jsPDF();
  const f = data.from || from;
  const t = data.to || to;
  header(doc, "Sales report", `Period: ${fmtDate(f)} to ${fmtDate(t)}`);

  const y = summary(doc, [
    ["Total sales", money(data.totalSales)],
    ["Orders", data.totalOrders],
    ["Average order", money(data.averageOrderValue)],
  ], 46);

  autoTable(doc, {
    startY: y + 4,
    head: [["Product", "SKU", "Units sold", "Revenue"]],
    body: data.byProduct.map((p) => [p.productName, p.sku, p.quantitySold, money(p.revenue)]),
    headStyles: { fillColor: ACCENT },
    columnStyles: { 2: { halign: "right" }, 3: { halign: "right" } },
    styles: { fontSize: 10 },
  });

  footer(doc);
  doc.save(`sales-report_${f}_to_${t}.pdf`);
}

export function inventoryPdf(data) {
  const doc = new jsPDF({ orientation: "landscape" });
  header(doc, "Inventory report", "Current stock levels and value");

  const y = summary(doc, [
    ["Products", data.totalProducts],
    ["Units in stock", data.totalUnits],
    ["Stock value (cost)", money(data.stockValueAtCost)],
    ["Stock value (selling)", money(data.stockValueAtSelling)],
    ["Low stock items", data.lowStockCount],
  ], 46);

  autoTable(doc, {
    startY: y + 4,
    head: [["Name", "SKU", "Category", "Stock", "Min", "Cost", "Price", "Status"]],
    body: data.products.map((p) => [
      p.name, p.sku, p.categoryName || "-", p.currentStock, p.minimumStock,
      money(p.purchasePrice), money(p.sellingPrice), p.lowStock ? "LOW" : "OK",
    ]),
    headStyles: { fillColor: ACCENT },
    columnStyles: { 3: { halign: "right" }, 4: { halign: "right" }, 5: { halign: "right" }, 6: { halign: "right" } },
    styles: { fontSize: 9 },
    didParseCell: (c) => {
      if (c.section === "body" && c.column.index === 7 && c.cell.raw === "LOW") {
        c.cell.styles.textColor = [200, 0, 0];
        c.cell.styles.fontStyle = "bold";
      }
    },
  });

  footer(doc);
  doc.save(`inventory-report_${new Date().toISOString().slice(0, 10)}.pdf`);
}

const billMoney = (n) =>
  "Rs. " + Number(n || 0).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

export function billPdf(o) {
  const doc = new jsPDF({ format: "a5" });
  const w = doc.internal.pageSize.getWidth();
  const h = doc.internal.pageSize.getHeight();

  doc.setFontSize(20);
  doc.setTextColor(...ACCENT);
  doc.text("BxoStock", 14, 18);
  doc.setFontSize(10);
  doc.setTextColor(110);
  doc.text("Supermarket bill", 14, 24);

  doc.setFontSize(11);
  doc.setTextColor(30);
  doc.text(`Bill no: #${o.id}`, w - 14, 18, { align: "right" });
  doc.setFontSize(9);
  doc.setTextColor(110);
  doc.text(o.createdAt ? new Date(o.createdAt).toLocaleString("en-IN") : "-", w - 14, 24, { align: "right" });

  doc.setDrawColor(220);
  doc.line(14, 28, w - 14, 28);

  doc.setFontSize(10);
  doc.setTextColor(30);
  doc.text(`Customer: ${o.customerName || "-"}`, 14, 35);
  doc.text(`Served by: ${o.createdBy || "-"}`, 14, 41);

  let endY = 47 + 8 * ((o.items || []).length + 1);
  autoTable(doc, {
    startY: 47,
    margin: { left: 14, right: 14 },
    head: [["Item", "Qty", "Rate", "Amount"]],
    body: (o.items || []).map((l) => [l.productName, l.quantity, billMoney(l.unitPrice), billMoney(l.subtotal)]),
    headStyles: { fillColor: ACCENT },
    columnStyles: { 1: { halign: "right" }, 2: { halign: "right" }, 3: { halign: "right" } },
    styles: { fontSize: 9 },
    didDrawPage: (d) => { endY = d.cursor.y; },
  });

  const y = endY + 8;
  doc.setDrawColor(220);
  doc.line(14, y - 4, w - 14, y - 4);
  doc.setFontSize(12);
  doc.setTextColor(30);
  doc.text("Total", 14, y + 2);
  doc.text(billMoney(o.totalAmount), w - 14, y + 2, { align: "right" });

  if (o.status === "CANCELLED") {
    doc.setFontSize(44);
    doc.setTextColor(200, 0, 0);
    doc.text("CANCELLED", w / 2, h / 2, { align: "center", angle: 30 });
  }

  doc.setFontSize(9);
  doc.setTextColor(140);
  doc.text("Thank you for shopping with us!", w / 2, h - 12, { align: "center" });

  doc.save(`bill_order_${o.id}.pdf`);
}

export async function auditPdf(logs, search, share) {
  const doc = new jsPDF({ orientation: "landscape" });
  const w = doc.internal.pageSize.getWidth();
  const h = doc.internal.pageSize.getHeight();
  const bad = (a) => /FAILED|LOCKED|BLOCKED|LIMITED|DEACTIVATED/.test(a || "");
  const warnings = logs.filter((l) => bad(l.action)).length;

  header(doc, "Audit report", search ? `Filter: "${search}"` : "All events (latest 100)");
  doc.setFontSize(10);
  doc.setTextColor(30);
  doc.text(`Events: ${logs.length}    Warnings (failed, locked or blocked): ${warnings}`, w - 14, 36, { align: "right" });

  autoTable(doc, {
    startY: 42,
    margin: { left: 14, right: 14 },
    head: [["Time", "User", "Action", "Details", "IP"]],
    body: logs.map((l) => [
      l.createdAt ? new Date(l.createdAt).toLocaleString("en-IN") : "-",
      l.actor || "-",
      l.action || "-",
      l.details || "-",
      l.ipAddress || "-",
    ]),
    headStyles: { fillColor: ACCENT },
    styles: { fontSize: 8, cellPadding: 2 },
    columnStyles: { 0: { cellWidth: 40 }, 1: { cellWidth: 55 }, 2: { cellWidth: 42 }, 4: { cellWidth: 30 } },
    didParseCell: (d) => {
      if (d.section === "body" && d.column.index === 2 && bad(d.cell.raw)) {
        d.cell.styles.textColor = [200, 0, 0];
        d.cell.styles.fontStyle = "bold";
      }
    },
  });

  const pages = doc.getNumberOfPages();
  for (let i = 1; i <= pages; i++) {
    doc.setPage(i);
    doc.setFontSize(8);
    doc.setTextColor(140);
    doc.text(`Page ${i} of ${pages}`, w - 14, h - 8, { align: "right" });
  }

  const name = `audit_report_${new Date().toISOString().slice(0, 10)}.pdf`;
  if (share && navigator.canShare) {
    const file = new File([doc.output("blob")], name, { type: "application/pdf" });
    if (navigator.canShare({ files: [file] })) {
      try {
        await navigator.share({ files: [file], title: "BxoStock audit report" });
        return;
      } catch (e) {
        if (e && e.name === "AbortError") return;
      }
    }
  }
  doc.save(name);
}