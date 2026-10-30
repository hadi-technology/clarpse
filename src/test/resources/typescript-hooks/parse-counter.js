// Preloaded into the TypeScript daemon process by the parse-cost tests (node --require).
// Observes ts.createProgram and nothing else: it records which programs were built and how many
// distinct SourceFile objects, that is parses, each file path has produced across them. It changes
// no behaviour. The totals are written to CLARPSE_PARSE_STATS when the process exits.
const Module = require("module");
const fs = require("fs");

const out = process.env.CLARPSE_PARSE_STATS;
const stats = { programs: [], parses: {}, libParses: {}, programMs: 0 };
const seen = new WeakSet();
const originalLoad = Module._load;

// The compiler's exports are non-configurable getters, so the module is handed out behind a proxy
// that answers createProgram with an observing wrapper and everything else untouched.
function observe(ts) {
  const createProgram = ts.createProgram;
  const observed = function observedCreateProgram(...args) {
    const options = args[0];
    const rootNames = Array.isArray(options) ? options : (options && options.rootNames) || [];
    const began = process.hrtime.bigint();
    const program = createProgram.apply(this, args);
    stats.programMs += Number(process.hrtime.bigint() - began) / 1e6;
    const files = program.getSourceFiles();
    for (const file of files) {
      if (seen.has(file)) {
        continue;
      }
      seen.add(file);
      const bucket = program.isSourceFileDefaultLibrary(file) ? stats.libParses : stats.parses;
      bucket[file.fileName] = (bucket[file.fileName] || 0) + 1;
    }
    const given = Array.isArray(options) ? args[1] : options && options.options;
    stats.programs.push({
      outDir: (given && given.outDir) || "",
      rootNames: Array.from(rootNames),
      fileCount: files.length
    });
    return program;
  };
  return new Proxy(ts, {
    get(target, key) {
      return key === "createProgram" ? observed : Reflect.get(target, key, target);
    }
  });
}

const proxies = new WeakMap();

Module._load = function load(request, parent, isMain) {
  const loaded = originalLoad.apply(this, arguments);
  if (loaded && typeof loaded.createProgram === "function" && typeof loaded.createSourceFile === "function"
      ) {
    if (!proxies.has(loaded)) {
      proxies.set(loaded, observe(loaded));
    }
    return proxies.get(loaded);
  }
  return loaded;
};

process.on("exit", () => {
  if (out) {
    fs.writeFileSync(out, JSON.stringify(stats));
  }
});
