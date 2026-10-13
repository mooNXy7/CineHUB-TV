(() => {
  "use strict";
  const boot = () => {
    const app = window.CineHUB;
    const channels = Array.isArray(window.catalogoCanais) ? window.catalogoCanais : [];
    if (!app || typeof app.registerContent !== "function" || !channels.length) return;
    const mapped = channels.map((c, i) => ({
      id: "official-channel-" + (c.nome || i).toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[^a-z0-9]+/g, "-"),
      title: c.nome || "Canal",
      type: "channel",
      view: "ao-vivo",
      category: c.categoria || "Aberta",
      description: c.descricao || "Acesse a página oficial do canal.",
      poster: c.logo || "",
      url: c.aoVivo || c.site || "",
      source: "Canal oficial"
    }));
    app.registerContent(mapped);
  };
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", boot, { once: true });
  else boot();
})();
