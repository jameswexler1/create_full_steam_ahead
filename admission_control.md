# Steam Admission Valve

## Purpose

The Steam Admission Valve is a pipe-mounted throttle for one Full Steam Ahead `steam_inlet`. It
regulates how much steam one engine may request from a shared steam network without blocking the
main pipe run feeding later cylinders.

The implemented valve has a copper pressure body and a controller tower taller than one block. The
authored north face connects to the engine inlet. Other horizontal faces accept Create Fluid Pipes
according to the detected terminal or through-branch topology. Vertical pipe connections remain
disabled.

## Control Modes

A normal Create-wrench click cycles the controller without rotating the valve:

1. **Manual / Telegraph**: the built-in lever controls admission from `0..15`. Right-click steps up;
   sneak-right-click steps down. A tuned Engine Order Telegraph item can bind the valve to that
   telegraph channel, after which the valve and loaded telegraphs synchronize both ways.
2. **Redstone Link**: the manual lever is hidden and two frequency slots appear on the controller.
   The received analogue signal directly controls admission: `0` closes and `15` fully opens. Empty
   frequencies retain the existing full-open bypass.

Newly placed valves start manually controlled and fully open. Valves loaded from worlds created
before the remodel retain Redstone Link mode for compatibility.

## Runtime Requirements

- Preserve `FluidPipeBlockEntity` transport and Create pipe attachments.
- Throttle only one uniquely detected active `steam_inlet`; never throttle the through-main.
- Preserve fair pressure/flow allocation by exposing one final `0..15` admission strength to
  `SteamNetworkManager`.
- Hide Redstone Link value boxes outside Redstone Link mode.
- Hide the manual mechanism outside Manual / Telegraph mode.
- Persist manual strength, receiver strength/frequencies, telegraph channel, and mode across reloads
  and moving-contraption transforms.

## Pipe Topology

The block automatically discovers its neighbouring active `steam_inlet` and Create Fluid Pipes. It must visually align without a wrench.

### 1. Terminal Valve

One pipe face and one steam-inlet face.

```text
Pipe -- Valve -- Steam Inlet
```

This supports a single engine at the end of a supply run. The supply pipe may be opposite or at
ninety degrees to the inlet.

### 2. Through-Branch Valve

Two opposite pipe faces and one steam-inlet face.

```text
Pipe -- Valve -- Pipe
          |
      Steam Inlet
```

This is the primary cylinder-bank state. The two pipe arms form a continuous main steam line. The smaller branch faces the engine inlet. Opening or closing the valve throttles only that engine's branch; it never blocks the main pipe line feeding later cylinders.

### 3. Unlinked Fitting

No valid active steam inlet is attached, or the neighbouring layout is ambiguous. The valve may retain its valid pipe arms but is mechanically inactive. It should not guess an engine connection.

More complex junctions remain ordinary Create pipework. The authored model uses local north as the
fixed steam-inlet face; native Create pipe attachments provide every other live horizontal arm and
rim. Vertical connections are always disabled.

## Telegraph Linking

Use the existing Engine Order Telegraph item workflow:

- Right-click a linked telegraph with a telegraph item to copy its channel.
- Right-click a manual-mode admission valve with the tuned item to bind it.
- Sneak-right-click the valve with a telegraph item to clear the valve's channel.
- The registry stores only loaded positions and must never force-load chunks.

## Model and Animation

- Static body/controller: baked block model.
- Manual track and stops: partial model visible only in manual mode.
- Manual lever: partial model translating vertically by four model units across `0..15`.
- Receiver panel: partial model visible only in Redstone Link mode; selected frequency items remain
  rendered by Create's `LinkBehaviour`.

The source model and provisional texture are:

- `new_models/attempt_at_admission_valve_manual_lever_v1.bbmodel`
- `new_models/attempt_at_admission_valve_manual_lever_v1.png`

The texture is intentionally provisional. Repaint it after functional validation while preserving
the current UV islands and animation groups.

## Texture and Material Direction

- Pipe arms and rims should match Create Fluid Pipe copper scale, pixel density, and silhouette.
- Use brass for the central pressure chamber and control-panel edging.
- Use dark steel/iron only for narrow actuator details and bolts.
- Use rivets sparingly. The block is compact; excessive panels or bolts will make it noisy.
- Any red should be limited to a small position/safety mark, not a dominant colour field.
- Maintain hard pixel edges, symmetric UV treatment, and no smooth gradients.

## Collision and Selection Shape

The collision/selection shape follows every static source cuboid, the manual lever's complete travel
envelope, and Create's live pipe arms/rims. It deliberately extends above the base block to cover the
controller tower. Source cuboids must remain non-overlapping because mode and topology changes are
live.

## In-Game Readability

At normal play distance, the player must immediately read:

1. Which fixed flange supplies the engine inlet.
2. Which pipe arms form the continuing steam line.
3. Whether the tower shows the manual track or Redstone Link receiver pads.
4. The current lever position in manual mode.

The inventory model presents the full manual-mode controller at a reduced Create-style item scale.
