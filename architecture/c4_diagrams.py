"""Generate macula-cam2me's two C4 diagrams into assets/.

Run:  python3 architecture/c4_diagrams.py

  c4-context.svg     Level 1: what the app talks to
  c4-components.svg  Level 2: what it is made of

Colours are plain classes with a prefers-color-scheme: dark block, no CSS
custom properties (rsvg-convert renders those black), so the SVGs render the
same on GitHub, in IDE previews and through rsvg-convert. After a change,
render each (rsvg-convert -w 1400 assets/c4-context.svg -o /tmp/c.png) and
look at it; keep architecture/README.md's tables in step.
"""
import os
from xml.sax.saxutils import escape

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets")

SANS = '"Atkinson Hyperlegible", "Segoe UI", system-ui, -apple-system, Helvetica, Arial, sans-serif'
MONO = '"JetBrains Mono", ui-monospace, Menlo, Consolas, monospace'

# Element kinds, each (light fill, light stroke, dark fill, dark stroke).
KINDS = {
    "b-sys": ("#F5E9CC", "#A56F0A", "#3A2E13", "#E3AA3C"),   # in scope
    "b-per": ("#E3EBF2", "#3B5B7A", "#1D2A36", "#8EB4D8"),   # person
    "b-mac": ("#E6E4EE", "#5E5A78", "#26243A", "#A9A4C8"),   # Macula infrastructure
    "b-ext": ("#ECEFED", "#8A9791", "#1C2421", "#7D8B85"),   # external system
    "b-cmp": ("#FFFFFF", "#8A9791", "#171F1C", "#7D8B85"),   # component or container
}

STYLE = f"""<style>
.t-name {{ font-family: {SANS}; font-size: 15px; font-weight: 700; }}
.t-desc {{ font-family: {SANS}; font-size: 12.5px; }}
.t-rel {{ font-family: {SANS}; font-size: 11.5px; paint-order: stroke; stroke-width: 4px; stroke-linejoin: round; }}
.t-code {{ font-family: {MONO}; font-size: 13px; font-weight: 600; }}
.t-type {{ font-family: {MONO}; font-size: 11.5px; }}
.t-bound {{ font-family: {MONO}; font-size: 12px; font-weight: 600; }}
.t-leg {{ font-family: {SANS}; font-size: 12px; }}
.edge {{ fill: none; stroke-width: 1.25; }}
.edge-later {{ fill: none; stroke-width: 1.25; stroke-dasharray: 5 4; }}
.bg {{ fill: #FFFFFF; }}
""" + "".join(f".{k} {{ fill: {v[0]}; stroke: {v[1]}; stroke-width: {2 if k == 'b-sys' else 1.25}; }}\n" for k, v in KINDS.items()) + """\
.b-later { fill: none; stroke: #8A9791; stroke-width: 1.25; stroke-dasharray: 5 4; }
.bound { fill: none; stroke: #B3BDB8; stroke-width: 1; }
.head { fill: #3B5B7A; }
.t-name, .t-code { fill: #16201D; }
.t-type, .t-bound, .t-leg { fill: #66736E; }
.t-desc { fill: #34413C; }
.t-rel { fill: #66736E; stroke: #FFFFFF; }
.edge, .edge-later { stroke: #8A9791; }
.arrowhead { fill: #8A9791; }
@media (prefers-color-scheme: dark) {
.bg { fill: #171F1C; }
""" + "".join(f".{k} {{ fill: {v[2]}; stroke: {v[3]}; }}\n" for k, v in KINDS.items()) + """\
.b-later { stroke: #7D8B85; }
.bound { stroke: #4A5752; }
.head { fill: #8EB4D8; }
.t-name, .t-code { fill: #E6ECE9; }
.t-type, .t-bound, .t-leg { fill: #93A09A; }
.t-desc { fill: #C3CDC8; }
.t-rel { fill: #93A09A; stroke: #171F1C; }
.edge, .edge-later { stroke: #7D8B85; }
.arrowhead { fill: #7D8B85; }
}
</style>"""

DEFS = ('<defs><marker id="a" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" '
        'orient="auto-start-reverse"><path class="arrowhead" d="M0 0L10 5L0 10z"/></marker></defs>')

LEGEND = [("b-sys", "In scope"), ("b-per", "Person"), ("b-mac", "Macula infrastructure"),
          ("b-ext", "External system"), ("b-cmp", "Container or component"),
          ("b-later", "Planned, not built")]
BOUNDARY = ("bound", "Boundary")


class Diagram:
    def __init__(self, name, title, w, h):
        self.name, self.title, self.w, self.h, self.parts = name, title, w, h, []

    def text(self, cls, x, y, s, anchor=None, rotate=None):
        a = f' text-anchor="{anchor}"' if anchor else ""
        r = f' transform="rotate({rotate} {x} {y})"' if rotate is not None else ""
        self.parts.append(f'<text class="{cls}" x="{x}" y="{y}"{a}{r}>{escape(s)}</text>')

    def box(self, cls, x, y, w, h, name, kind, desc, person=False, code=False):
        # A box that is too small for its own words renders as text on top of the
        # next box, which no test would catch and a reader would read as the
        # diagram saying something it does not. Fail here instead.
        need = 70 + 16 * max(len(desc) - 1, 0) + 18
        if h < need:
            raise ValueError(f"{self.name}: {name} needs height {need}, has {h}")
        for line in desc:
            if 16 + 6.2 * len(line) > w - 16:
                raise ValueError(f"{self.name}: {name}: '{line}' is wider than {w - 32}px")
        self.parts.append(f'<rect class="{cls}" x="{x}" y="{y}" width="{w}" height="{h}" rx="10"/>')
        tx = x + 16
        if person:
            self.parts.append(f'<circle class="head" cx="{x + 26}" cy="{y + 24}" r="9"/>')
            tx = x + 44
        self.text("t-code" if code else "t-name", tx, y + 28, name)
        self.text("t-type", tx, y + 46, kind)
        for i, line in enumerate(desc):
            self.text("t-desc", x + 16, y + 70 + 16 * i, line)

    def bound(self, x, y, w, h, label):
        self.parts.append(f'<rect class="bound" x="{x}" y="{y}" width="{w}" height="{h}" rx="12"/>')
        self.text("t-bound", x + 16, y + 22, label)

    def edge(self, d, later=False):
        self.parts.append(f'<path class="{"edge-later" if later else "edge"}" d="{d}" marker-end="url(#a)"/>')

    def legend(self, y, boundary=True):
        x = 24
        for cls, label in LEGEND + ([BOUNDARY] if boundary else []):
            self.parts.append(f'<rect class="{cls}" x="{x}" y="{y}" width="22" height="14" rx="3"/>')
            self.text("t-leg", x + 30, y + 12, label)
            x += 34 + 6.6 * len(label)

    def write(self):
        svg = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {self.w} {self.h}" role="img" '
               f'aria-label="{escape(self.title)}">', f"<title>{escape(self.title)}</title>", STYLE, DEFS,
               f'<rect class="bg" x="0" y="0" width="{self.w}" height="{self.h}"/>'] + self.parts + ["</svg>", ""]
        with open(os.path.join(OUT, self.name), "w") as f:
            f.write("\n".join(svg))


def context():
    d = Diagram("c4-context.svg", "macula-cam2me system context", 1000, 760)
    d.box("b-per", 40, 30, 300, 120, "Phone owner", "[Person]",
          ["Installs the app, enters their own", "number, adds contacts by number,",
           "picks auto or a fixed station"], person=True)
    d.box("b-per", 660, 30, 300, 120, "Contact", "[Person]",
          ["Another phone running cam2me whose", "number the owner added. Later: dials",
           "in to watch the camera (#6)"], person=True)
    d.box("b-sys", 340, 210, 320, 152, "macula-cam2me", "[Android app; iOS skeleton]",
          ["One node identity per phone. Holds a", "pool of up to 3 stations, publishes and",
           "hears presence on cam2me.presence,", "matches it to contacts. No camera yet"])
    d.box("b-ext", 40, 450, 280, 136, "Android platform", "[External system]",
          ["The Keystore holds the node key;", "coarse location picks the nearest",
           "stations"])
    d.box("b-mac", 360, 450, 280, 136, "Macula stations", "[Macula infrastructure]",
          ["QUIC links (each station proves its", "node id), the pubsub fan-out of",
           "cam2me.presence, and the route", "for list_stations. Wire 1.0"])
    d.box("b-mac", 680, 450, 280, 136, "mcl-stations", "[Macula infrastructure]",
          ["The station directory, nearest", "first, in io.macula: trusted under",
           "the realm key the app pins"])
    d.edge("M190 150V180H400V210")
    d.text("t-rel", 200, 168, "number, contacts, station mode")
    d.edge("M810 150V180H600V210", later=True)
    d.text("t-rel", 620, 168, "watches the camera (planned, #6)")
    d.edge("M400 362V410H180V450"); d.text("t-rel", 196, 402, "node key, last location")
    d.edge("M500 362V450"); d.text("t-rel", 508, 418, "one pool, up to 3 links;")
    d.text("t-rel", 508, 434, "presence pub/sub")
    d.edge("M600 362V410H820V450"); d.text("t-rel", 620, 402, "list_stations near (lat, lng)")
    d.text("t-desc", 40, 616, "The contact is the same app on another phone: both publish a heartbeat on cam2me.presence through every station they hold,")
    d.text("t-desc", 40, 634, "and each matches what it hears against its own contacts. Pull model: a viewer will dial the phone through one of those stations;")
    d.text("t-desc", 40, 652, "nothing listens on the phone itself. The presence payload needs a redesign before any build is distributed (#4).")
    d.legend(700, boundary=False)
    d.write()


def components():
    d = Diagram("c4-components.svg", "macula-cam2me components", 1000, 940)
    d.bound(16, 40, 968, 670, "macula-cam2me Android app [Container: Kotlin, Compose]")
    d.box("b-cmp", 32, 70, 456, 120, "MainActivity", "[Compose entry point]",
          ["Wires the node key, station connect, the heartbeat and",
           "the contact list together; reconnects when the station",
           "preference or the location permission changes"], code=True)
    d.box("b-cmp", 512, 70, 456, 120, "onboarding", "[OnboardingScreen, OwnerPhoneNumber]",
          ["Asks once for the owner's own phone number and keeps it",
           "in DataStore; presence publishes its hash"], code=True)
    d.box("b-cmp", 32, 220, 456, 150, "reachability", "[NodeKeyPair, MeshPool, StationDiscovery]",
          ["NodeKeyPair: one pq_hybrid key per install, in the Keystore.",
           "StationPreference: auto (nearest 3 by coarse location) or a",
           "fixed station. MeshPool: one pool over up to 3 stations,",
           "each pinned to its node id; io.macula's realm key pinned"], code=True)
    d.box("b-cmp", 512, 220, 456, 150, "presence", "[PresenceHeartbeat, PhonePresence]",
          ["One loop on the pool: every 30 s publish {phone_hash,",
           "stations} on cam2me.presence, and match what it hears",
           "against the contacts; a match stores the publisher's",
           "node id and stations in phone_presence"], code=True)
    d.box("b-mac", 32, 420, 456, 120, "macula-rust-ffi", "[Rust, UniFFI Kotlin bindings, via cam2me-ffi]",
          ["FfiNodeKey, FfiPool (connect, call, publish, subscribe,",
           "status), FfiSubscription. 0.13, wire 1.0"], code=True)
    d.box("b-cmp", 512, 420, 456, 120, "Cam2MeDatabase", "[Room, schema v1]",
          ["Two tables: contacts and phone_presence"], code=True)
    d.box("b-cmp", 32, 570, 456, 120, "Keyring", "[io.crates.keyring, NDK context]",
          ["Initialises the Android keystore context the Rust",
           "keystore needs before NodeKeyPair runs"], code=True)
    d.box("b-cmp", 512, 570, 456, 120, "contacts", "[Contact, ContactDao, ContactListScreen]",
          ["The owner's contacts by phone number, with each one's",
           "last presence (online, node id, stations)"], code=True)
    d.box("b-cmp", 32, 740, 456, 104, "iOS app", "[SwiftUI skeleton, XcodeGen]",
          ["Generates a pq_hybrid key each launch, shows the node id;", "no mesh, no presence yet (#5)"], code=True)
    d.box("b-later", 512, 740, 456, 104, "camera stream", "[planned: capture, serve, dial]",
          ["Capture and serve the stream; a contact dials in", "through a station (#6)"], code=True)
    d.edge("M488 130H512"); d.text("t-rel", 500, 168, "asks once", anchor="middle", rotate=-90)
    d.edge("M260 190V220"); d.text("t-rel", 268, 212, "connects on launch")
    d.edge("M420 190V205H700V220"); d.text("t-rel", 712, 212, "starts one on the pool")
    d.edge("M260 370V420"); d.text("t-rel", 268, 412, "connect, call, status")
    d.edge("M600 370V390H420V420"); d.text("t-rel", 432, 410, "publish, subscribe, next")
    d.edge("M800 370V420"); d.text("t-rel", 812, 394, "reads contacts,"); d.text("t-rel", 812, 410, "writes phone_presence")
    d.edge("M740 570V540"); d.text("t-rel", 752, 562, "reads and writes")
    d.edge("M260 540V570"); d.text("t-rel", 268, 562, "keystore load and save")
    d.text("t-desc", 40, 870, "rust/cam2me-ffi is only a build target: it re-exports macula-rust-ffi so the linker keeps it, and scripts/build-rust-*.sh")
    d.text("t-desc", 40, 886, "cross-compile it and regenerate the Kotlin and Swift bindings on every build; no generated code is committed.")
    d.legend(912, boundary=False)
    d.write()


if __name__ == "__main__":
    for make in (context, components):
        make()
