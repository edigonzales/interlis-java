import { DiagramCanvas, mountDiagramControls } from './dist/diagram-ui.js';

const element = document.getElementById('canvas');
const controlsElement = document.getElementById('controls');
const worker = new URL('./dist/diagram-layout-worker.js', import.meta.url);
let canvas, controls;

function clear() {
    controls?.dispose();
    canvas?.dispose();
    canvas = controls = undefined;
    document.getElementById('status').textContent = 'Compile model files to show a diagram.';
}

function payload(format, artifact) {
    if (format === 'SVG') return artifact.svg;
    let binary = '';
    for (const byte of artifact.data) binary += String.fromCharCode(byte);
    return btoa(binary);
}

async function artifact(view, format, scope) {
    return format === 'SVG' ? view.exportSvg(scope) : view.exportPng(scope);
}

async function show(json, session) {
    clear();
    try {
        const dto = JSON.parse(json);
        const current = canvas = new DiagramCanvas(element, {
            navigate: id => report('SELECTION', id),
            status: (state, message) => report(state, message)
        }, worker);
        controls = mountDiagramControls(controlsElement, current, {
            error: message => report('ERROR', message),
            export: async (format, scope) => {
                try {
                    const result = await artifact(current, format, scope);
                    if (current === canvas) window.javaHost.exportFile(format, payload(format, result));
                } catch (failure) { report('EXPORT_ERROR', String(failure)); }
            }
        });
        if (!await current.accept(dto, session)) {
            if (current === canvas) report('ERROR', 'The compiled projection could not be rendered.');
            return;
        }
        if (current !== canvas) return;
        controls.refresh();
        if (window.javaHost.isProbe()) await probe(current, dto);
    } catch (failure) { report('ERROR', String(failure?.stack || failure)); }
}

async function probe(view, dto) {
    if (dto.inputKind !== 'CHECKED_SCHEMA' || !dto.nodes.some(node => node.canonicalName === 'GuiExample.Records.Record'))
        throw Error('Missing class from the live compilation');
    const svgElement = element.querySelector('svg.sprotty-graph');
    if (!svgElement?.querySelector('[data-kind="CLASS"]')) throw Error('No rendered class in Sprotty SVG');
    const before = view.getViewport();
    await view.zoom(1.2);
    await view.pan(30, 20);
    const after = view.getViewport();
    if (after.zoom === before.zoom || after.x === before.x) throw Error('Zoom/pan did not change viewport');
    await view.select([dto.nodes[0].id]);
    if (!view.selectedIds().includes(dto.nodes[0].id)) throw Error('Selection missing');
    const svg = await artifact(view, 'SVG', 'FULL');
    const png = await artifact(view, 'PNG', 'FULL');
    window.javaHost.exportFile('SVG', payload('SVG', svg));
    window.javaHost.exportFile('PNG', payload('PNG', png));
    report('PROBE_RENDERED', JSON.stringify({ nodes: dto.nodes.length, pngWidth: png.width, pngHeight: png.height, viewport: after }));
}

window.guiDiagram = { clear, show };
report('CAPABILITIES', JSON.stringify({ modules: true, worker: typeof Worker, webAssembly: typeof WebAssembly,
    crypto: typeof crypto?.subtle, userAgent: navigator.userAgent }));
report('HOST_READY', 'Compile model files to show a diagram.');
