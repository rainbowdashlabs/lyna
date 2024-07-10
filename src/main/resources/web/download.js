const host = "{{ HOST }}"

function products() {
    return document.getElementById('products');
}

function types() {
    return document.getElementById('types');
}

function assets() {
    return document.getElementById('assets');
}

function clearTypes() {
    types().innerHTML = ''
}

function clearAssets() {
    assets().innerHTML = ''
}

function createEntry(id = null, className = null, content = null) {
    let entry = document.createElement("div")
    if (id) entry.id = id
    if (content) entry.textContent = content
    if (className) entry.classList.add(className)
    return entry
}

async function fetchJson(url) {
    try {
        let response = await fetch(url);
        if (!response.ok) {
            console.error(`Error: ${response.status} ${response.statusText}`)
            return []
        }
        return await response.json()
    } catch (error) {
        console.error(`Error: ${error.message}`)
    }
    return []
}

window.addEventListener("DOMContentLoaded", async () => listProducts(), false)

async function listProducts() {
    for (let item of await fetchJson("/api/v1/products")) {
        let entry = createEntry(item.id, "entry")
        entry.addEventListener('click', async (e) => listTypes(e), false)
        entry.classList.add("flex-entry")
        entry.classList.add("clickable")
        entry.appendChild(createEntry(null, "primary-line", item.name))
        entry.appendChild(createEntry(null, "secondary-line", "")) // This will be a tagline in the future
        let button = document.createElement("button");
        button.classList.add("fa")
        button.classList.add("fa-link")
        button.classList.add("download_button")
        button.addEventListener('click', e => openPage(item.url));
        entry.appendChild(button);

        products().appendChild(entry);
    }
}

async function listTypes(event) {
    let product = event.target.parentElement.id
    if (isNaN(Number(product))) {
        product = event.target.id
    }
    clearTypes()
    clearAssets()
    for (let item of await fetchJson("/api/v1/releases/" + product)) {
        let entry = createEntry(product + "/" + item.id, "entry")
        entry.classList.add("clickable")
        entry.addEventListener('click', async (e) => listAssets(e))
        entry.appendChild(createEntry(null, "primary-line", item.name))
        entry.appendChild(createEntry(null, "secondary-line", item.description))
        types().appendChild(entry)
    }
}

async function listAssets(event) {
    let productType = event.target.parentElement.id
    if (productType.match("[]")) {
        productType = event.target.id
    }

    clearAssets()
    for (let item of await fetchJson("/api/v1/releases/" + productType)) {
        let id = productType + "/" + item.version
        let entry = createEntry(productType + "/" + item.version, "entry")
        entry.classList.add("flex-entry")
        let text = document.createElement("div")
        text.appendChild(createEntry(null, "primary-line", item.version))
        let date = new Date(item.published * 1000)
        text.appendChild(createEntry(null, "secondary-line", date.toLocaleString()))
        entry.appendChild(text)
        let button = document.createElement("button");
        button.id = id
        button.classList.add("fa")
        button.classList.add("fa-download")
        button.classList.add("download_button")
        button.addEventListener('click', downloadButton);
        entry.appendChild(button);


        assets().appendChild(entry)
    }
}

function downloadButton(event) {
    let id = event.target.id
    event.stopPropagation();
    window.open(`/api/v1/download/direct/${id.replace("-", "/")}`, '_blank');
}

function openPage(url) {
    window.open(url)
}
