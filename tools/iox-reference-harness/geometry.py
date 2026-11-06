"""Render the bounded, straight-segment XTF 2.3 geometry profiles."""

MAX_OBJECTS = 1024


def grid(count, seed, spacing):
    if not 1 <= count <= MAX_OBJECTS or not 1 <= seed <= 5 or spacing not in (2, 3):
        raise ValueError("Grid profile is outside the fixed geometry bounds")
    for index in range(count):
        x = seed + index % 32 * spacing
        y = seed + index // 32 * spacing
        corners = [(x, y), (x + 2, y), (x + 2, y + 2), (x, y + 2), (x, y)]
        yield [(f"{x}.00", f"{y}.00") for x, y in corners]


def transfer(case):
    profile = case["workload"]
    count = profile["objects"]
    if not 1 <= count <= MAX_OBJECTS:
        raise ValueError("Generated case exceeds the object bound")
    spec = case["generate"]
    rings = list(grid(count, spec["seed"], spec["spacing"])) if "spacing" in spec else spec["rings"]
    if len(rings) != count or profile["polygons"] != count or profile["rings"] != count:
        raise ValueError("Geometry profile has inconsistent object, polygon or ring counts")
    if (any(len(ring) != 5 or ring[0] != ring[-1] for ring in rings)
            or profile["straightSegments"] != 4 * count or profile["arcSegments"] != 0):
        raise ValueError("Generated profiles require closed four-segment straight rings")

    model, topic, kind = spec["class"].split(".")
    basket = f"{model}.{topic}"
    tag = f"{basket}.{kind}"
    lines = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">',
        '  <HEADERSECTION VERSION="2.3" SENDER="iox-reference">',
        "    <MODELS>",
        f'      <MODEL NAME="{model}" VERSION="2026-11-06" URI="https://example.invalid/iox-reference"/>',
        "    </MODELS>",
        "  </HEADERSECTION>",
        "  <DATASECTION>",
        f'    <{basket} BID="parcel-basket">',
    ]
    for index, ring in enumerate(rings, start=1):
        lines.extend([
            f'      <{tag} TID="parcel-{index}">',
            "        <shape><SURFACE><BOUNDARY><POLYLINE>",
            *(f"          <COORD><C1>{x}</C1><C2>{y}</C2></COORD>" for x, y in ring),
            "        </POLYLINE></BOUNDARY></SURFACE></shape>",
            f"      </{tag}>",
        ])
    lines.extend([f"    </{basket}>", "  </DATASECTION>", "</TRANSFER>", ""])
    return "\n".join(lines).encode("utf-8")
