#!/usr/bin/env node

const fs = require("fs");
const path = require("path");
const zlib = require("zlib");

const ROOT = path.resolve(__dirname, "../..");
const MODEL_PATH = path.join(ROOT, "new_models/attempt_at_admission_valve_manual_lever_v1.bbmodel");
const TEXTURE_NAME = "attempt_at_admission_valve_manual_lever_v1.png";
const TEXTURE_PATH = path.join(ROOT, "new_models", TEXTURE_NAME);
const ATLAS_SIZE = 128;
const PADDING = 2;

const hex = value => {
    const rgb = Number.parseInt(value.replace("#", ""), 16);
    return [(rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 255];
};

const C = {
    copperOutline: hex("#3D161E"),
    copperShadow: hex("#5B2924"),
    copperDeep: hex("#793B2B"),
    copperDark: hex("#904931"),
    copperBase: hex("#A75A40"),
    copperMid: hex("#B26247"),
    copperLight: hex("#C26B4C"),
    copperHigh: hex("#D67B5B"),
    copperSpec: hex("#E3826C"),
    brassOutline: hex("#602C25"),
    brassShadow: hex("#895A3B"),
    brassDark: hex("#AA733F"),
    brassBase: hex("#C9974C"),
    brassMid: hex("#D6AE5B"),
    brassLight: hex("#E4C66A"),
    brassHigh: hex("#F1DC78"),
    brassSpec: hex("#FFF387"),
    steelBlack: hex("#2B2B2B"),
    steelShadow: hex("#3B3B39"),
    steelDark: hex("#464647"),
    steelBase: hex("#51544A"),
    steelMid: hex("#62615A"),
    steelLight: hex("#746C61"),
    steelHigh: hex("#837C73"),
    redOutline: hex("#511C1C"),
    redDark: hex("#8C1616"),
    red: hex("#D41E1E"),
    recess: hex("#0A0505")
};

const pixels = new Uint8Array(ATLAS_SIZE * ATLAS_SIZE * 4);

function setPixel(x, y, color) {
    if (x < 0 || y < 0 || x >= ATLAS_SIZE || y >= ATLAS_SIZE)
        return;
    const index = (y * ATLAS_SIZE + x) * 4;
    pixels.set(color, index);
}

function getPixel(x, y) {
    const index = (y * ATLAS_SIZE + x) * 4;
    return pixels.slice(index, index + 4);
}

function fill(x, y, width, height, color) {
    for (let py = y; py < y + height; py++)
        for (let px = x; px < x + width; px++)
            setPixel(px, py, color);
}

function hLine(x, y, width, color) {
    fill(x, y, width, 1, color);
}

function vLine(x, y, height, color) {
    fill(x, y, 1, height, color);
}

function bevel(x, y, width, height, light, shadow) {
    if (height > 1) {
        hLine(x, y, width, light);
        hLine(x, y + height - 1, width, shadow);
    }
    if (width > 1) {
        vLine(x, y, height, light);
        vLine(x + width - 1, y, height, shadow);
    }
}

function rivet(x, y, light, shadow, width, height) {
    if (x < 0 || y < 0 || x >= width || y >= height)
        return;
    setPixel(x, y, light);
    if (x + 1 < width && y + 1 < height)
        setPixel(x + 1, y + 1, shadow);
}

function localPainter(originX, originY, width, height) {
    return {
        width,
        height,
        set: (x, y, color) => setPixel(originX + x, originY + y, color),
        fill: (x, y, w, h, color) => fill(originX + x, originY + y, w, h, color),
        hLine: (x, y, w, color) => hLine(originX + x, originY + y, w, color),
        vLine: (x, y, h, color) => vLine(originX + x, originY + y, h, color),
        bevel: (light, shadow) => bevel(originX, originY, width, height, light, shadow),
        rivet: (x, y, light, shadow) => {
            if (x < 0 || y < 0 || x >= width || y >= height)
                return;
            setPixel(originX + x, originY + y, light);
            if (x + 1 < width && y + 1 < height)
                setPixel(originX + x + 1, originY + y + 1, shadow);
        }
    };
}

function paintCopperPanel(p, face) {
    const {width: w, height: h} = p;
    const base = face === "up" ? C.copperLight : face === "down" ? C.copperDark : C.copperBase;
    p.fill(0, 0, w, h, base);
    p.bevel(C.copperHigh, C.copperShadow);

    if (w >= 4 && h >= 4) {
        p.fill(1, 1, w - 2, h - 2, C.copperMid);
        p.hLine(1, 1, w - 2, C.copperLight);
        p.hLine(1, h - 2, w - 2, C.copperDark);
    }
    if (w >= 9 && h >= 4) {
        const seam = Math.floor(w / 2);
        p.vLine(seam, 1, h - 2, C.copperDark);
        if (seam + 1 < w - 1)
            p.vLine(seam + 1, 1, h - 2, C.copperHigh);
    }
    if (w >= 6 && h >= 4) {
        p.rivet(1, 1, C.copperSpec, C.copperShadow);
        p.rivet(w - 2, 1, C.copperSpec, C.copperShadow);
        p.rivet(1, h - 2, C.copperHigh, C.copperShadow);
        p.rivet(w - 2, h - 2, C.copperHigh, C.copperShadow);
    }
}

function paintCopperBand(p, face) {
    const {width: w, height: h} = p;
    p.fill(0, 0, w, h, face === "down" ? C.copperDark : C.copperLight);
    p.bevel(C.copperSpec, C.copperDeep);
    if (h >= 2)
        p.hLine(0, h - 1, w, C.copperDark);
    if (w >= 6) {
        for (let x = 2; x < w - 1; x += 4)
            p.rivet(x, Math.min(1, h - 1), C.copperSpec, C.copperShadow);
    }
}

function paintBrass(p, face, framed = false) {
    const {width: w, height: h} = p;
    const base = face === "up" ? C.brassLight : face === "down" ? C.brassDark : C.brassBase;
    p.fill(0, 0, w, h, base);
    p.bevel(C.brassHigh, C.brassShadow);

    if (framed && w >= 4 && h >= 4) {
        p.fill(1, 1, w - 2, h - 2, C.brassMid);
        p.hLine(1, 1, w - 2, C.brassLight);
        p.hLine(1, h - 2, w - 2, C.brassDark);
    }
    if (w >= 7 && h >= 4) {
        p.rivet(1, 1, C.brassSpec, C.brassShadow);
        p.rivet(w - 2, 1, C.brassSpec, C.brassShadow);
        p.rivet(1, h - 2, C.brassHigh, C.brassShadow);
        p.rivet(w - 2, h - 2, C.brassHigh, C.brassShadow);
    }
}

function paintBrassBand(p, face) {
    const {width: w, height: h} = p;
    p.fill(0, 0, w, h, face === "down" ? C.brassDark : C.brassBase);
    p.bevel(C.brassHigh, C.brassShadow);
    if (h >= 2)
        p.hLine(0, h - 1, w, C.brassDark);
    if (w >= 6) {
        for (let x = 2; x < w - 1; x += 4)
            p.set(x, Math.min(1, h - 1), C.brassSpec);
    }
}

function paintIronPanel(p, face) {
    const {width: w, height: h} = p;
    const base = face === "up" ? C.steelMid : face === "down" ? C.steelShadow : C.steelDark;
    p.fill(0, 0, w, h, base);
    p.bevel(C.steelLight, C.steelBlack);
    if (w >= 4 && h >= 4) {
        p.fill(1, 1, w - 2, h - 2, C.steelBase);
        p.hLine(1, h - 2, w - 2, C.steelShadow);
    }
    if (w >= 7 && h >= 6) {
        p.rivet(1, 1, C.steelHigh, C.steelShadow);
        p.rivet(w - 2, 1, C.steelHigh, C.steelShadow);
        p.rivet(1, h - 2, C.steelLight, C.steelShadow);
        p.rivet(w - 2, h - 2, C.steelLight, C.steelShadow);
    }
}

function paintSteelRail(p) {
    const {width: w, height: h} = p;
    p.fill(0, 0, w, h, C.steelBase);
    p.bevel(C.steelHigh, C.steelShadow);
    if (w >= 2 && h >= 5) {
        for (let y = 2; y < h - 1; y += 3)
            p.set(Math.floor(w / 2), y, C.steelDark);
    }
}

function paintBrassGuide(p) {
    const {width: w, height: h} = p;
    p.fill(0, 0, w, h, C.brassBase);
    p.bevel(C.brassHigh, C.brassShadow);
    if (w >= 2 && h >= 5) {
        for (let y = 2; y < h - 1; y += 3) {
            p.set(Math.min(1, w - 1), y, C.brassSpec);
            if (y + 1 < h)
                p.set(Math.min(1, w - 1), y + 1, C.brassDark);
        }
    }
}

function paintTrack(p) {
    const {width: w, height: h} = p;
    p.fill(0, 0, w, h, C.steelShadow);
    p.bevel(C.steelMid, C.recess);
    if (w >= 3 && h >= 3)
        p.fill(1, 1, w - 2, h - 2, C.steelBlack);
    if (w >= 3) {
        for (let y = 2; y < h - 1; y += 2)
            p.hLine(1, y, w - 2, C.steelDark);
    }
}

function paintRedGrip(p) {
    const {width: w, height: h} = p;
    p.fill(0, 0, w, h, C.redDark);
    p.bevel(C.red, C.redOutline);
    if (w >= 3 && h >= 3)
        p.fill(1, 1, w - 2, h - 2, C.redDark);
}

function paintInletFace(p) {
    const {width: w, height: h} = p;
    p.fill(0, 0, w, h, C.copperLight);
    p.bevel(C.copperSpec, C.copperShadow);
    if (w >= 8 && h >= 8) {
        p.fill(2, 2, w - 4, h - 4, C.copperDark);
        p.fill(3, 3, w - 6, h - 6, C.copperBase);
        p.fill(4, 4, Math.max(1, w - 8), Math.max(1, h - 8), C.recess);
        for (const [x, y] of [[1, 1], [w - 2, 1], [1, h - 2], [w - 2, h - 2]])
            p.rivet(x, y, C.brassSpec, C.copperShadow);
    }
}

function paintBrassFlange(p) {
    const {width: w, height: h} = p;
    p.fill(0, 0, w, h, C.brassBase);
    p.bevel(C.brassHigh, C.brassShadow);
    if (w >= 6 && h >= 6) {
        p.fill(1, 1, w - 2, h - 2, C.brassDark);
        p.fill(2, 2, w - 4, h - 4, C.copperDark);
        p.rivet(1, 1, C.brassSpec, C.brassOutline);
        p.rivet(w - 2, 1, C.brassSpec, C.brassOutline);
        p.rivet(1, h - 2, C.brassHigh, C.brassOutline);
        p.rivet(w - 2, h - 2, C.brassHigh, C.brassOutline);
    }
}

function paintRearHatch(p) {
    const {width: w, height: h} = p;
    p.fill(0, 0, w, h, C.brassDark);
    p.bevel(C.brassHigh, C.brassOutline);
    if (w >= 4 && h >= 4) {
        p.fill(1, 1, w - 2, h - 2, C.copperDark);
        p.fill(2, 2, Math.max(1, w - 4), Math.max(1, h - 4), C.copperBase);
    }
    if (w >= 5 && h >= 5) {
        p.rivet(1, 1, C.brassSpec, C.copperShadow);
        p.rivet(w - 2, 1, C.brassSpec, C.copperShadow);
        p.rivet(1, h - 2, C.brassHigh, C.copperShadow);
        p.rivet(w - 2, h - 2, C.brassHigh, C.copperShadow);
    }
}

function paintFace(faceInfo, x, y) {
    const p = localPainter(x, y, faceInfo.width, faceInfo.height);
    const {name, face} = faceInfo;

    if (name === "inlet_connector" && face === "north") {
        paintInletFace(p);
    } else if (name === "connecting_base" && (face === "north" || face === "south")) {
        paintBrassFlange(p);
    } else if (name === "controller_rear_hatch" && face === "north") {
        paintRearHatch(p);
    } else if (name === "controller_center_track") {
        paintTrack(p);
    } else if (name === "manual_lever_grip" || name === "manual_lever_grip_face") {
        paintRedGrip(p);
    } else if (name.includes("slider")) {
        paintSteelRail(p);
    } else if (name.includes("guide")) {
        paintBrassGuide(p);
    } else if (name === "controller_tower_core" || name === "controller_rear_plate") {
        paintIronPanel(p, face);
    } else if (name.includes("track_") && name.includes("stop")) {
        paintBrass(p, face, false);
    } else if (name === "manual_lever_crossbar") {
        paintBrass(p, face, false);
    } else if (name === "valve_body_lower_band" || name === "valve_body_upper_band" ||
               name === "controller_plinth_upper_band") {
        paintBrassBand(p, face);
    } else if (name === "connecting_base" || name.includes("frame") ||
               name === "controller_top_cap" || name === "controller_top_ridge") {
        paintBrass(p, face, true);
    } else if (name === "inlet_connector") {
        paintCopperBand(p, face);
    } else {
        paintCopperPanel(p, face);
    }
}

function bleedIsland(x, y, width, height) {
    for (let offset = 1; offset <= 1; offset++) {
        for (let px = 0; px < width; px++) {
            setPixel(x + px, y - offset, getPixel(x + px, y));
            setPixel(x + px, y + height - 1 + offset, getPixel(x + px, y + height - 1));
        }
        for (let py = 0; py < height; py++) {
            setPixel(x - offset, y + py, getPixel(x, y + py));
            setPixel(x + width - 1 + offset, y + py, getPixel(x + width - 1, y + py));
        }
        setPixel(x - offset, y - offset, getPixel(x, y));
        setPixel(x + width - 1 + offset, y - offset, getPixel(x + width - 1, y));
        setPixel(x - offset, y + height - 1 + offset, getPixel(x, y + height - 1));
        setPixel(x + width - 1 + offset, y + height - 1 + offset, getPixel(x + width - 1, y + height - 1));
    }
}

function faceDimensions(element, face) {
    const dx = element.to[0] - element.from[0];
    const dy = element.to[1] - element.from[1];
    const dz = element.to[2] - element.from[2];
    const dimensions = {
        north: [dx, dy],
        south: [dx, dy],
        east: [dz, dy],
        west: [dz, dy],
        up: [dx, dz],
        down: [dx, dz]
    }[face];
    return dimensions.map(value => Math.max(1, Math.ceil(value)));
}

function makeCrcTable() {
    const table = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
        let c = n;
        for (let k = 0; k < 8; k++)
            c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
        table[n] = c >>> 0;
    }
    return table;
}

const CRC_TABLE = makeCrcTable();

function crc32(buffer) {
    let crc = 0xFFFFFFFF;
    for (const byte of buffer)
        crc = CRC_TABLE[(crc ^ byte) & 0xFF] ^ (crc >>> 8);
    return (crc ^ 0xFFFFFFFF) >>> 0;
}

function pngChunk(type, data) {
    const typeBuffer = Buffer.from(type, "ascii");
    const body = Buffer.concat([typeBuffer, data]);
    const out = Buffer.alloc(12 + data.length);
    out.writeUInt32BE(data.length, 0);
    body.copy(out, 4);
    out.writeUInt32BE(crc32(body), 8 + data.length);
    return out;
}

function encodePng(width, height, rgba) {
    const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
    const ihdr = Buffer.alloc(13);
    ihdr.writeUInt32BE(width, 0);
    ihdr.writeUInt32BE(height, 4);
    ihdr[8] = 8;
    ihdr[9] = 6;
    const rows = Buffer.alloc((width * 4 + 1) * height);
    for (let y = 0; y < height; y++) {
        const rowOffset = y * (width * 4 + 1);
        rows[rowOffset] = 0;
        Buffer.from(rgba.buffer, rgba.byteOffset + y * width * 4, width * 4).copy(rows, rowOffset + 1);
    }
    return Buffer.concat([
        signature,
        pngChunk("IHDR", ihdr),
        pngChunk("IDAT", zlib.deflateSync(rows, {level: 9})),
        pngChunk("IEND", Buffer.alloc(0))
    ]);
}

function renderOrthographic(model, face, outputPath) {
    const scale = 12;
    const margin = 16;
    const projections = {
        south: {
            horizontal: 0, vertical: 1,
            depth: element => element.to[2],
            sort: (a, b) => a.depth - b.depth,
            flipHorizontal: false
        },
        north: {
            horizontal: 0, vertical: 1,
            depth: element => element.from[2],
            sort: (a, b) => b.depth - a.depth,
            flipHorizontal: true
        },
        east: {
            horizontal: 2, vertical: 1,
            depth: element => element.to[0],
            sort: (a, b) => a.depth - b.depth,
            flipHorizontal: true
        },
        west: {
            horizontal: 2, vertical: 1,
            depth: element => element.from[0],
            sort: (a, b) => b.depth - a.depth,
            flipHorizontal: false
        },
        up: {
            horizontal: 0, vertical: 2,
            depth: element => element.to[1],
            sort: (a, b) => a.depth - b.depth,
            flipHorizontal: false
        }
    };
    const projection = projections[face];
    const candidates = model.elements
        .filter(element => element.faces && element.faces[face])
        .map(element => ({element, depth: projection.depth(element)}))
        .sort(projection.sort);

    const horizontalValues = model.elements.flatMap(element => [element.from[projection.horizontal], element.to[projection.horizontal]]);
    const verticalValues = model.elements.flatMap(element => [element.from[projection.vertical], element.to[projection.vertical]]);
    const minHorizontal = Math.min(...horizontalValues);
    const maxHorizontal = Math.max(...horizontalValues);
    const minVertical = Math.min(...verticalValues);
    const maxVertical = Math.max(...verticalValues);
    const width = Math.ceil((maxHorizontal - minHorizontal) * scale) + margin * 2;
    const height = Math.ceil((maxVertical - minVertical) * scale) + margin * 2;
    const output = new Uint8Array(width * height * 4);

    function compositePixel(x, y, color) {
        if (x < 0 || y < 0 || x >= width || y >= height || color[3] === 0)
            return;
        const index = (y * width + x) * 4;
        output.set(color, index);
    }

    for (const {element} of candidates) {
        const faceData = element.faces[face];
        const h0 = element.from[projection.horizontal];
        const h1 = element.to[projection.horizontal];
        const v0 = element.from[projection.vertical];
        const v1 = element.to[projection.vertical];
        const drawWidth = Math.max(1, Math.round((h1 - h0) * scale));
        const drawHeight = Math.max(1, Math.round((v1 - v0) * scale));
        const baseX = margin + Math.round((h0 - minHorizontal) * scale);
        const baseY = margin + Math.round((maxVertical - v1) * scale);
        const [u0, vTex0, u1, vTex1] = faceData.uv;
        for (let py = 0; py < drawHeight; py++) {
            const texY = Math.min(vTex1 - 1, Math.floor(vTex0 + py / drawHeight * (vTex1 - vTex0)));
            for (let px = 0; px < drawWidth; px++) {
                const normalizedX = projection.flipHorizontal ? (drawWidth - 1 - px) : px;
                const texX = Math.min(u1 - 1, Math.floor(u0 + normalizedX / drawWidth * (u1 - u0)));
                compositePixel(baseX + px, baseY + py, getPixel(texX, texY));
            }
        }
    }
    fs.writeFileSync(outputPath, encodePng(width, height, output));
}

const model = JSON.parse(fs.readFileSync(MODEL_PATH, "utf8"));
const faceInfos = [];

for (const element of model.elements) {
    element.autouv = 0;
    for (const [face, faceData] of Object.entries(element.faces || {})) {
        if (!faceData)
            continue;
        const [width, height] = faceDimensions(element, face);
        faceInfos.push({element, faceData, name: element.name, face, width, height});
    }
}

faceInfos.sort((a, b) =>
    (b.height + PADDING * 2) - (a.height + PADDING * 2) ||
    (b.width + PADDING * 2) - (a.width + PADDING * 2) ||
    a.name.localeCompare(b.name) || a.face.localeCompare(b.face));

let cursorX = 0;
let cursorY = 0;
let rowHeight = 0;

for (const info of faceInfos) {
    const packedWidth = info.width + PADDING * 2;
    const packedHeight = info.height + PADDING * 2;
    if (cursorX + packedWidth > ATLAS_SIZE) {
        cursorX = 0;
        cursorY += rowHeight;
        rowHeight = 0;
    }
    if (cursorY + packedHeight > ATLAS_SIZE)
        throw new Error(`Texture atlas overflow at ${info.name}:${info.face}`);

    const x = cursorX + PADDING;
    const y = cursorY + PADDING;
    info.faceData.uv = [x, y, x + info.width, y + info.height];
    info.faceData.texture = 0;
    delete info.faceData.rotation;
    paintFace(info, x, y);
    bleedIsland(x, y, info.width, info.height);

    info.x = x;
    info.y = y;
    cursorX += packedWidth;
    rowHeight = Math.max(rowHeight, packedHeight);
}

model.resolution = {width: ATLAS_SIZE, height: ATLAS_SIZE};
const png = encodePng(ATLAS_SIZE, ATLAS_SIZE, pixels);
const texture = {
    name: TEXTURE_NAME,
    path: `new_models/${TEXTURE_NAME}`,
    folder: "block",
    namespace: "full_steam_ahead",
    id: "0",
    group: "",
    scope: 0,
    width: ATLAS_SIZE,
    height: ATLAS_SIZE,
    uv_width: ATLAS_SIZE,
    uv_height: ATLAS_SIZE,
    particle: true,
    use_as_default: true,
    layers_enabled: false,
    sync_to_project: "",
    file_format: "png",
    render_mode: "default",
    render_sides: "auto",
    wrap_mode: "limited",
    pbr_channel: "color",
    fps: 7,
    frame_time: 1,
    frame_order_type: "loop",
    frame_order: "",
    frame_interpolate: false,
    visible: true,
    internal: true,
    saved: true,
    uuid: "fa230000-0000-4000-8000-000000000001",
    source: `data:image/png;base64,${png.toString("base64")}`
};
model.textures = [texture];

fs.writeFileSync(TEXTURE_PATH, png);
fs.writeFileSync(MODEL_PATH, `${JSON.stringify(model, null, 2)}\n`);

for (const face of ["south", "north", "east", "west", "up"])
    renderOrthographic(model, face, `/tmp/admission_manual_v1_${face}.png`);

const usedHeight = cursorY + rowHeight;
console.log(`Textured ${faceInfos.length} faces in ${ATLAS_SIZE}x${ATLAS_SIZE} (${usedHeight}px used height).`);
console.log(`Wrote ${path.relative(ROOT, MODEL_PATH)}`);
console.log(`Wrote ${path.relative(ROOT, TEXTURE_PATH)}`);
