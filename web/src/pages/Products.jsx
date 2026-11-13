import { useEffect, useState } from "react";
import api from "../api";
import useDebounce from "../useDebounce";
import { useConfirm } from "../confirmContext";

const emptyForm = {
  name: "", sku: "", description: "", categoryId: "",
  purchasePrice: "", sellingPrice: "", minimumStock: "0", openingStock: "0",
};

function errText(err) {
  if (!err.response) return "Cannot reach the server. It may be waking up, so try again in a minute.";
  const d = err.response.data;
  if (d?.errors) return Object.entries(d.errors).map(([f, m]) => `${f}: ${m}`).join(", ");
  return d?.message || "Something went wrong.";
}

const money = (n) => "₹" + Number(n).toLocaleString("en-IN");

export default function Products() {
  const confirm = useConfirm();
  const user = JSON.parse(localStorage.getItem("user") || "{}");
  const isAdmin = user.role === "ADMIN";

  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [keyword, setKeyword] = useState("");
  const debounced = useDebounce(keyword.trim(), 300);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [modal, setModal] = useState(null); // null | { mode: "add" } | { mode: "edit", id }
  const [form, setForm] = useState(emptyForm);
  const [formError, setFormError] = useState("");
  const [saving, setSaving] = useState(false);

  async function load(kw) {
    setLoading(true);
    setError("");
    try {
      const res = await api.get("/products", { params: kw ? { keyword: kw } : {} });
      setProducts(res.data.filter((p) => p.active !== false));
    } catch (err) {
      setError(errText(err));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    api.get("/categories").then((r) => setCategories(r.data)).catch(() => {});
  }, []);

  useEffect(() => {
    let ignore = false;
    api.get("/products", { params: debounced ? { keyword: debounced } : {} })
      .then((res) => {
        if (!ignore) { setProducts(res.data.filter((p) => p.active !== false)); setError(""); }
      })
      .catch((err) => { if (!ignore) setError(errText(err)); })
      .finally(() => { if (!ignore) setLoading(false); });
    return () => { ignore = true; };
  }, [debounced]);


  function openAdd() {
    setForm(emptyForm);
    setFormError("");
    setModal({ mode: "add" });
  }

  function openEdit(p) {
    setForm({
      name: p.name,
      sku: p.sku,
      description: p.description || "",
      categoryId: p.categoryId ?? "",
      purchasePrice: p.purchasePrice,
      sellingPrice: p.sellingPrice,
      minimumStock: p.minimumStock,
      openingStock: "0",
    });
    setFormError("");
    setModal({ mode: "edit", id: p.id });
  }

  function change(e) {
    setForm({ ...form, [e.target.name]: e.target.value });
  }

  async function save(e) {
    e.preventDefault();
    setFormError("");
    setSaving(true);
    const body = {
      name: form.name.trim(),
      sku: form.sku.trim(),
      description: form.description.trim(),
      categoryId: form.categoryId === "" ? null : Number(form.categoryId),
      purchasePrice: Number(form.purchasePrice),
      sellingPrice: Number(form.sellingPrice),
      minimumStock: Number(form.minimumStock || 0),
    };
    try {
      if (modal.mode === "add") {
        body.openingStock = Number(form.openingStock || 0);
        await api.post("/products", body);
      } else {
        await api.put(`/products/${modal.id}`, body);
      }
      setModal(null);
      load(keyword.trim());
    } catch (err) {
      setFormError(err.response?.status === 409 ? "This SKU already exists." : errText(err));
    } finally {
      setSaving(false);
    }
  }

  async function remove(p) {
        const ok = await confirm({
      title: "Delete product",
      message: `Delete "${p.name}"? It will no longer appear in the product list.`,
      confirmText: "Delete",
      danger: true,
    });
    if (!ok) return;
    try {
      await api.delete(`/products/${p.id}`);
      load(keyword.trim());
    } catch (err) {
      setError(errText(err));
    }
  }

  return (
    <div>
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <h2 className="m-0">Products</h2>
        {isAdmin && (
          <button className="btn btn-accent" onClick={openAdd}>+ Add product</button>
        )}
      </div>

        <input
        className="form-control mb-3"
        placeholder="Search by name or SKU"
        autoComplete="off"
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
      />

      {error && <div className="alert alert-danger py-2">{error}</div>}
      {loading && <p className="text-secondary">Loading...</p>}
      {!loading && !error && products.length === 0 && (
        <p className="text-secondary">No products found.</p>
      )}

      {/* Table: tablets and larger */}
      {products.length > 0 && (
        <div className="table-responsive d-none d-md-block">
          <table className="table table-dark align-middle">
            <thead>
              <tr>
                <th>Name</th><th>SKU</th><th>Category</th>
                <th className="text-end">Price</th>
                <th className="text-end">Stock</th>
                {isAdmin && <th></th>}
              </tr>
            </thead>
            <tbody>
              {products.map((p) => (
                <tr key={p.id}>
                  <td>{p.name}</td>
                  <td>{p.sku}</td>
                  <td>{p.categoryName || "-"}</td>
                  <td className="text-end">{money(p.sellingPrice)}</td>
                  <td className="text-end">
                    {p.currentStock}{" "}
                    {p.lowStock && <span className="badge bg-danger">Low</span>}
                  </td>
                  {isAdmin && (
                    <td className="text-end text-nowrap">
                      <button className="btn btn-sm btn-outline-light me-2" onClick={() => openEdit(p)}>Edit</button>
                      <button className="btn btn-sm btn-outline-danger" onClick={() => remove(p)}>Delete</button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Cards: phones */}
      <div className="d-md-none">
        {products.map((p) => (
          <div className="product-card" key={p.id}>
            <div className="d-flex justify-content-between">
              <strong>{p.name}</strong>
              {p.lowStock && <span className="badge bg-danger">Low</span>}
            </div>
            <div className="text-secondary small">{p.sku} · {p.categoryName || "No category"}</div>
            <div className="d-flex justify-content-between mt-2">
              <span>{money(p.sellingPrice)}</span>
              <span>Stock: {p.currentStock}</span>
            </div>
            {isAdmin && (
              <div className="d-flex gap-2 mt-3">
                <button className="btn btn-sm btn-outline-light flex-fill" onClick={() => openEdit(p)}>Edit</button>
                <button className="btn btn-sm btn-outline-danger flex-fill" onClick={() => remove(p)}>Delete</button>
              </div>
            )}
          </div>
        ))}
      </div>

      {/* Add / Edit form */}
      {modal && (
        <>
          <div className="modal d-block" tabIndex="-1">
            <div className="modal-dialog modal-dialog-scrollable">
              <form className="modal-content app-modal" onSubmit={save}>
                <div className="modal-header">
                  <h5 className="modal-title">{modal.mode === "add" ? "Add product" : "Edit product"}</h5>
                  <button type="button" className="btn-close btn-close-white" onClick={() => setModal(null)} />
                </div>
                <div className="modal-body">
                  {formError && <div className="alert alert-danger py-2">{formError}</div>}

                  <label className="form-label">Name</label>
                  <input className="form-control mb-3" name="name" value={form.name} onChange={change} required />

                  <label className="form-label">SKU</label>
                  <input className="form-control mb-3" name="sku" value={form.sku} onChange={change} required />

                  <label className="form-label">Category</label>
                  <select className="form-select form-control mb-3" name="categoryId" value={form.categoryId} onChange={change}>
                    <option value="">No category</option>
                    {categories.map((c) => (
                      <option key={c.id} value={c.id}>{c.name}</option>
                    ))}
                  </select>

                  <label className="form-label">Description</label>
                  <input className="form-control mb-3" name="description" value={form.description} onChange={change} />

                  <div className="row">
                    <div className="col-6 mb-3">
                      <label className="form-label">Purchase price</label>
                      <input type="number" min="0" step="0.01" className="form-control" name="purchasePrice" value={form.purchasePrice} onChange={change} required />
                    </div>
                    <div className="col-6 mb-3">
                      <label className="form-label">Selling price</label>
                      <input type="number" min="0" step="0.01" className="form-control" name="sellingPrice" value={form.sellingPrice} onChange={change} required />
                    </div>
                    <div className="col-6 mb-3">
                      <label className="form-label">Minimum stock</label>
                      <input type="number" min="0" className="form-control" name="minimumStock" value={form.minimumStock} onChange={change} required />
                    </div>
                    {modal.mode === "add" && (
                      <div className="col-6 mb-3">
                        <label className="form-label">Opening stock</label>
                        <input type="number" min="0" className="form-control" name="openingStock" value={form.openingStock} onChange={change} />
                      </div>
                    )}
                  </div>
                </div>
                <div className="modal-footer">
                  <button type="button" className="btn btn-outline-light" onClick={() => setModal(null)}>Cancel</button>
                  <button type="submit" className="btn btn-accent" disabled={saving}>
                    {saving ? "Saving..." : "Save"}
                  </button>
                </div>
              </form>
            </div>
          </div>
          <div className="modal-backdrop show"></div>
        </>
      )}
    </div>
  );
}