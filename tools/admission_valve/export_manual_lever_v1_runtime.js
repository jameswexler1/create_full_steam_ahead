#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../..");
const SOURCE_MODEL = path.join(
    ROOT,
    "new_models/attempt_at_admission_valve_manual_lever_v1.bbmodel"
);
const SOURCE_TEXTURE = path.join(
    ROOT,
    "new_models/attempt_at_admission_valve_manual_lever_v1.png"
);
const ASSET_ROOT = path.join(ROOT, "src/main/resources/assets/full_steam_ahead");
const MODEL_ROOT = path.join(ASSET_ROOT, "models");
const VALVE_MODEL_ROOT = path.join(MODEL_ROOT, "block/steam_admission_valve");
const PARTIAL_ROOT = path.join(MODEL_ROOT, "block/partial");
const TEXTURE_TARGET = path.join(ASSET_ROOT, "textures/block/steam_admission_valve.png");
const BLOCKSTATE_TARGET = path.join(ASSET_ROOT, "blockstates/steam_admission_valve.json");

const MANUAL_MECHANISM = new Set([
    "controller_left_guide",
    "controller_right_guide",
    "controller_center_track",
    "left_track_lower_stop",
    "right_track_lower_stop",
    "left_track_upper_stop",
    "right_track_upper_stop"
]);

function writeJson(target, value) {
    fs.mkdirSync(path.dirname(target), {recursive: true});
    fs.writeFileSync(target, `${JSON.stringify(value, null, 2)}\n`);
}

function runtimeElement(element, textureSize) {
    if (element.rotation && element.rotation.some(value => value !== 0)) {
        throw new Error(`Rotated source cuboid is not supported: ${element.name}`);
    }

    const faces = {};
    for (const [direction, face] of Object.entries(element.faces || {})) {
        if (!face || face.texture === null || face.texture === undefined) {
            continue;
        }
        const converted = {
            uv: face.uv.map(value => value * 16 / textureSize),
            texture: "#texture"
        };
        if (face.rotation) {
            converted.rotation = face.rotation;
        }
        faces[direction] = converted;
    }

    const converted = {
        name: element.name,
        from: element.from,
        to: element.to,
        faces
    };
    if (element.shade === false) {
        converted.shade = false;
    }
    return converted;
}

function model(elements, display) {
    const result = {
        credit: "Create: Full Steam Ahead",
        parent: "block/block",
        render_type: "minecraft:cutout_mipped",
        textures: {
            texture: "full_steam_ahead:block/steam_admission_valve",
            particle: "full_steam_ahead:block/steam_admission_valve"
        },
        elements
    };
    if (display) {
        result.display = display;
    }
    return result;
}

function receiverPad(name, fromX, toX, faceUv) {
    return {
        name,
        from: [fromX, 22, 10],
        to: [toX, 26, 10.5],
        faces: {
            north: {uv: [11, 2, 13, 2.5], texture: "#link"},
            east: {uv: [11, 2, 13, 2.5], texture: "#link"},
            south: {uv: faceUv, texture: "#link"},
            west: {uv: [11, 2, 13, 2.5], texture: "#link"},
            up: {uv: [11, 2, 13, 2.5], texture: "#link"},
            down: {uv: [11, 2, 13, 2.5], texture: "#link"}
        }
    };
}

const HORIZONTAL_DIRECTIONS = [
    {name: "north", y: 0},
    {name: "east", y: 90},
    {name: "south", y: 180},
    {name: "west", y: 270}
];

function bodyBlockstateEntry(facing, y, inverted) {
    const apply = {model: "full_steam_ahead:block/steam_admission_valve/body"};
    if (inverted) {
        apply.x = 180;
    }
    const effectiveY = (y + (inverted ? 180 : 0)) % 360;
    if (effectiveY !== 0) {
        apply.y = effectiveY;
    }
    return {when: {facing, inverted: String(inverted)}, apply};
}

function pipeArmEntries() {
    return HORIZONTAL_DIRECTIONS.flatMap(({name: facing}) =>
        HORIZONTAL_DIRECTIONS
            .filter(({name: direction}) => direction !== facing)
            .map(({name: direction}) => ({
                when: {facing, [direction]: "true"},
                apply: {model: `create:block/fluid_pipe/connection/${direction}`}
            }))
    );
}

const source = JSON.parse(fs.readFileSync(SOURCE_MODEL, "utf8"));
const textureSize = source.resolution?.width || 128;
if (textureSize !== (source.resolution?.height || textureSize)) {
    throw new Error("Admission valve source texture must be square");
}

const staticElements = [];
const mechanismElements = [];
const leverElements = [];
for (const element of source.elements) {
    const converted = runtimeElement(element, textureSize);
    if (element.name.startsWith("manual_lever_")) {
        leverElements.push(converted);
    } else if (MANUAL_MECHANISM.has(element.name)) {
        mechanismElements.push(converted);
    } else {
        staticElements.push(converted);
    }
}

if (staticElements.length !== 16 || mechanismElements.length !== 7 || leverElements.length !== 5) {
    throw new Error(
        `Unexpected model split: ${staticElements.length} static, `
        + `${mechanismElements.length} mechanism, ${leverElements.length} lever`
    );
}

const itemDisplay = {
    gui: {rotation: [30, 225, 0], translation: [0, -3.5, 0], scale: [0.45, 0.45, 0.45]},
    ground: {translation: [0, 2, 0], scale: [0.2, 0.2, 0.2]},
    fixed: {translation: [0, -3, 0], scale: [0.34, 0.34, 0.34]},
    thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, 1, 0], scale: [0.27, 0.27, 0.27]},
    thirdperson_lefthand: {rotation: [75, 45, 0], translation: [0, 1, 0], scale: [0.27, 0.27, 0.27]},
    firstperson_righthand: {rotation: [0, 45, 0], translation: [0, -2, 0], scale: [0.3, 0.3, 0.3]},
    firstperson_lefthand: {rotation: [0, 225, 0], translation: [0, -2, 0], scale: [0.3, 0.3, 0.3]}
};

writeJson(path.join(VALVE_MODEL_ROOT, "body.json"), model(staticElements));
writeJson(
    path.join(PARTIAL_ROOT, "steam_admission_valve_manual_mechanism.json"),
    model(mechanismElements)
);
writeJson(
    path.join(PARTIAL_ROOT, "steam_admission_valve_manual_lever.json"),
    model(leverElements)
);
writeJson(path.join(PARTIAL_ROOT, "steam_admission_valve_receiver_panel.json"), {
    credit: "Create: Full Steam Ahead",
    parent: "block/block",
    render_type: "minecraft:cutout_mipped",
    textures: {
        link: "create:block/redstone_bridge",
        particle: "create:block/redstone_bridge"
    },
    elements: [
        receiverPad("First frequency pad", 3.5, 7.5, [11, 0, 13, 2]),
        receiverPad("Second frequency pad", 8.5, 12.5, [13, 0, 15, 2])
    ]
});
writeJson(
    path.join(VALVE_MODEL_ROOT, "item.json"),
    model([...staticElements, ...mechanismElements, ...leverElements], itemDisplay)
);

writeJson(BLOCKSTATE_TARGET, {
    multipart: [
        ...HORIZONTAL_DIRECTIONS.flatMap(({name, y}) => [
            bodyBlockstateEntry(name, y, false),
            bodyBlockstateEntry(name, y, true)
        ]),
        ...pipeArmEntries()
    ]
});

fs.mkdirSync(path.dirname(TEXTURE_TARGET), {recursive: true});
fs.copyFileSync(SOURCE_TEXTURE, TEXTURE_TARGET);

console.log(`Exported ${staticElements.length} static cuboids`);
console.log(`Exported ${mechanismElements.length} manual mechanism cuboids`);
console.log(`Exported ${leverElements.length} animated lever cuboids`);
console.log("Exported connection-only Create pipe arms without center-pipe overlap");
