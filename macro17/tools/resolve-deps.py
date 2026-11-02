"""Resolve o conjunto de dependências Maven (POM e Gradle Module Metadata) e monta um repositório local.
Uso: python3 resolve.py <saida> g:a:v [g:a:v ...]"""
import sys, os, re, json, io, zipfile, hashlib, urllib.request, urllib.error
import xml.etree.ElementTree as ET

REPOS = ["https://dl.google.com/dl/android/maven2", "https://repo1.maven.org/maven2"]
SKIP_GROUPS = ("org.jetbrains.kotlin", "org.jetbrains.kotlinx")   # o runtime do Kotlin já vem de libs/
SKIP_COORDS = {("org.jetbrains", "annotations")}
KEEP_ABI = "arm64-v8a"
CACHE = {}

def get(path):
    if path in CACHE: return CACHE[path]
    for base in REPOS:
        for attempt in range(4):
            try:
                with urllib.request.urlopen(base + "/" + path, timeout=120) as r:
                    data = r.read(); CACHE[path] = (data, base); return CACHE[path]
            except urllib.error.HTTPError as e:
                if e.code == 404: break
                import time; time.sleep(3 * (attempt + 1))
            except Exception:
                import time; time.sleep(3 * (attempt + 1))
    CACHE[path] = (None, None); return CACHE[path]

def p(g, a, v, ext): return f"{g.replace('.', '/')}/{a}/{v}/{a}-{v}.{ext}"

def vkey(v):
    return [(0, int(x)) if x.isdigit() else (-1, x) for x in re.split(r"[.\-]", v)]

NS = "{http://maven.apache.org/POM/4.0.0}"
def tx(el, tag):
    c = el.find(NS + tag) if el is not None else None
    return c.text.strip() if c is not None and c.text else None

def load_pom(g, a, v):
    data, _ = get(p(g, a, v, "pom"))
    return ET.fromstring(data) if data else None

def props_of(root, chain):
    props = {}
    for pom in reversed(chain):
        pr = pom.find(NS + "properties")
        if pr is not None:
            for c in pr: props[c.tag.replace(NS, "")] = (c.text or "").strip()
    return props

def interp(s, props):
    if s is None: return None
    for _ in range(5):
        s2 = re.sub(r"\$\{([^}]+)\}", lambda m: props.get(m.group(1), m.group(0)), s)
        if s2 == s: break
        s = s2
    return s

def pom_chain(root):
    chain = [root]
    while True:
        par = chain[-1].find(NS + "parent")
        if par is None: break
        pg, pa, pv = tx(par, "groupId"), tx(par, "artifactId"), tx(par, "version")
        pp = load_pom(pg, pa, pv)
        if pp is None: break
        chain.append(pp)
    return chain

def pom_deps(g, a, v):
    root = load_pom(g, a, v)
    if root is None: return None, []
    chain = pom_chain(root)
    props = props_of(root, chain)
    props["project.version"] = v; props["project.groupId"] = g; props["version"] = v
    props["pom.version"] = v
    managed = {}
    for pom in chain:
        dm = pom.find(NS + "dependencyManagement")
        if dm is None: continue
        for d in dm.iter(NS + "dependency"):
            managed[(tx(d, "groupId"), tx(d, "artifactId"))] = interp(tx(d, "version"), props)
    out = []
    for d in root.iter(NS + "dependency"):
        if d.getparent is not None if False else False: pass
    deps = root.find(NS + "dependencies")
    if deps is not None:
        for d in deps.findall(NS + "dependency"):
            scope = tx(d, "scope") or "compile"
            if scope not in ("compile", "runtime") or (tx(d, "optional") == "true"): continue
            dg, da = interp(tx(d, "groupId"), props), interp(tx(d, "artifactId"), props)
            dv = interp(tx(d, "version"), props) or managed.get((dg, da))
            if dv: out.append((dg, da, dv))
    return (tx(root, "packaging") or "jar"), out

def module_deps(g, a, v):
    data, _ = get(p(g, a, v, "module"))
    if not data: return None
    m = json.loads(data); out = []; redirect = None
    for var in m.get("variants", []):
        n = var.get("name", "").lower()
        if "source" in n or "javadoc" in n: continue
        for at in var.get("available-at", []) or []:
            redirect = (at["module"] and at["group"], at["module"], at["version"])
        if not ("runtime" in n or "api" in n): continue
        for d in var.get("dependencies", []) or []:
            ver = d.get("version", {})
            vv = ver.get("requires") or ver.get("prefers") or ver.get("strictly")
            if vv: out.append((d["group"], d["module"], vv.strip("[]()").split(",")[0]))
    return out, redirect

def skip(g, a): return g.startswith(SKIP_GROUPS) or (g, a) in SKIP_COORDS

def main(out, roots):
    selected = {}; seen = {}; queue = []
    for r in roots:
        g, a, v = r.split(":"); queue.append((g, a, v))
    while queue:
        g, a, v = queue.pop(0)
        if skip(g, a): continue
        seen.setdefault((g, a), set()).add(v)
        if (g, a) in selected and vkey(selected[(g, a)]) >= vkey(v): continue
        selected[(g, a)] = v
        md = module_deps(g, a, v)
        if md is not None:
            deps, redirect = md
            if redirect: queue.append((redirect[0], redirect[1], redirect[2]))
        else:
            _, deps = pom_deps(g, a, v)
        for d in deps: queue.append(d)
    print(f"{len(selected)} módulos selecionados")
    total = 0
    for (g, a), v in sorted(selected.items()):
        for ver in sorted(seen[(g, a)], key=vkey):
            for ext in ("pom", "module"):
                data, _ = get(p(g, a, ver, ext))
                if data: write(out, p(g, a, ver, ext), data)
        # artefato principal só da versão escolhida
        packaging = "jar"
        data, _ = get(p(g, a, v, "pom"))
        if data:
            r = ET.fromstring(data); packaging = tx(r, "packaging") or "jar"
        if packaging == "pom": print(f"  {g}:{a}:{v}  (pom)"); continue
        ext = "aar" if packaging == "aar" else "jar"
        blob, _ = get(p(g, a, v, ext))
        if blob is None:
            alt = "jar" if ext == "aar" else "aar"; blob, _ = get(p(g, a, v, alt)); ext = alt if blob else ext
        if blob is None: print(f"  !! sem artefato: {g}:{a}:{v}"); continue
        if ext == "aar": blob = strip_abi(blob)
        write(out, p(g, a, v, ext), blob); total += len(blob)
        print(f"  {g}:{a}:{v}  {ext}  {len(blob)/1024:.0f} KB  (versões vistas: {sorted(seen[(g,a)], key=vkey)})")
    # POMs que o Gradle também lê (pais e BOMs): o resolvedor os baixou para calcular as dependências, então devem ir junto.
    for path, (data, _) in list(CACHE.items()):
        if data and path.endswith((".pom", ".module")) and not os.path.exists(os.path.join(out, path)):
            write(out, path, data); print(f"  + {path}")
    print(f"total de artefatos: {total/1e6:.1f} MB")

def write(out, path, data):
    full = os.path.join(out, path); os.makedirs(os.path.dirname(full), exist_ok=True)
    open(full, "wb").write(data)

def strip_abi(blob):
    zin = zipfile.ZipFile(io.BytesIO(blob)); buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zout:
        for it in zin.infolist():
            if it.filename.startswith("jni/") and not it.filename.startswith(f"jni/{KEEP_ABI}/") and not it.is_dir(): continue
            if it.filename.startswith("prefab/"): continue   # pacote C++ (cabeçalhos e .so de todas as ABIs): o app só usa o JNI
            zout.writestr(it, zin.read(it.filename))
    return buf.getvalue()

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2:])
