const pptxgen = require("../build/node_modules/pptxgenjs");
const pres = new pptxgen();
pres.layout = "LAYOUT_16x9";
pres.author = "Team Nimbus Nova";
pres.title = "NOVA - NASA Space Apps Challenge";
pres.theme = { headFontFace: "Arial", bodyFontFace: "Arial" };

const BG = "F7F8FA", INK = "141A2E", NAVY = "111830", MUTED = "5B6478", BLUE = "2B6CB0", ORANGE = "E8743B", LINE = "E3E7EE", WHITE = "FFFFFF";
const img = name => `img/${name}.png`;
const text = (slide, value, options) => slide.addText(value, { isTextBox: true, margin: 0, fontFace: "Arial", color: INK, valign: "top", ...options });
const card = (slide, x, y, w, h, fill) => slide.addShape(pres.ShapeType.roundRect, { x, y, w, h, rectRadius: 0.06, fill: { color: fill || WHITE },
    line: { color: LINE, width: 0.75 }, shadow: { type: "outer", color: "1B2440", opacity: 0.08, blur: 8, offset: 2, angle: 90 } });

function content(kicker, title, number) {
    const slide = pres.addSlide();
    slide.background = { color: BG };
    text(slide, kicker, { x: 0.5, y: 0.38, w: 8, h: 0.2, fontSize: 9, color: BLUE, charSpacing: 1 });
    text(slide, title, { x: 0.5, y: 0.6, w: 9, h: 0.5, fontSize: 24, bold: true });
    text(slide, "NOVA", { x: 0.5, y: 5.25, w: 2, h: 0.2, fontSize: 8, color: MUTED, charSpacing: 2 });
    text(slide, String(number), { x: 9, y: 5.25, w: 0.5, h: 0.2, fontSize: 8, color: MUTED, align: "right" });
    return slide;
}

// 1 Title
let slide = pres.addSlide();
slide.background = { color: NAVY };
slide.addImage({ path: img("title"), x: 4.6, y: 0, w: 5.4, h: 5.625 });
slide.addImage({ path: img("logo"), x: 0.5, y: 0.45, w: 0.55, h: 0.55 });
text(slide, "NASA SPACE APPS CHALLENGE 2026  •  BUCHAREST", { x: 0.5, y: 1.55, w: 3.9, h: 0.2, fontSize: 9, color: "7FB2E5", charSpacing: 1 });
text(slide, "NOVA", { x: 0.5, y: 1.8, w: 3.9, h: 0.75, fontSize: 48, bold: true, color: WHITE });
text(slide, [
    { text: "“The best way to keep a prisoner from escaping is to make sure he never knows he’s in prison.”", options: { italic: true, color: WHITE, breakLine: true } },
    { text: "Fyodor Dostoevsky", options: { fontSize: 9, color: "9AA6BF" } }
], { x: 0.5, y: 2.75, w: 3.7, h: 0.95, fontSize: 12.5, paraSpaceAfter: 4 });
text(slide, "Kids reject forced study. With NOVA they play Minecraft and learn space science without realizing it.",
    { x: 0.5, y: 3.9, w: 3.7, h: 0.6, fontSize: 11, color: "C9D2E3" });
text(slide, "Challenge: Build a Junior Astronaut Mission Trainer", { x: 0.5, y: 5.05, w: 3.9, h: 0.2, fontSize: 8.5, color: "9AA6BF" });
slide.addNotes("Not all children want to study, but almost all of them want to play.");

// 2 Problem
slide = content("01 / RELEVANCE & IMPACT", "Kids love games. They resist forced study.", 2);
const problem = [
    ["THE PROBLEM", "Forced study ≠ fun", "Educational platforms feel like chores. After the novelty fades, the logins stop and kids go back to their games.", INK],
    ["THE NOVA SOLUTION", "Put the lesson inside the game", "A Minecraft mod where the class trains as an astronaut crew. They play a space mission and pick up the science on the way.", BLUE]
];
problem.forEach(([label, head, body, color], index) => {
    const y = 1.45 + index * 1.75;
    card(slide, 0.5, y, 4.3, 1.55);
    text(slide, label, { x: 0.72, y: y + 0.2, w: 3.9, h: 0.18, fontSize: 8.5, color: MUTED, charSpacing: 1 });
    text(slide, head, { x: 0.72, y: y + 0.44, w: 3.9, h: 0.32, fontSize: 16, bold: true, color });
    text(slide, body, { x: 0.72, y: y + 0.82, w: 3.9, h: 0.6, fontSize: 11, color: MUTED });
});
slide.addImage({ path: img("whiteboard"), x: 5.1, y: 1.45, w: 4.4, h: 2.82, rounding: false });
text(slide, "Where NOVA started: our first whiteboard sketch", { x: 5.1, y: 4.36, w: 4.4, h: 0.2, fontSize: 9, color: MUTED, italic: true });
text(slide, "What if the lesson was the game?", { x: 5.1, y: 4.62, w: 4.4, h: 0.3, fontSize: 14, bold: true, color: ORANGE });
slide.addNotes("Over time, this becomes a problem because students may stop using educational platforms and spend their time playing games instead.\n\nSo, how did we solve this? We created NOVA, a Minecraft mod designed to train students through space missions. You might say that we simply created a basic space-themed game, and that's perfectly fine, because the whole idea is to help children learn without even realizing it.");

// 3 How it works
slide = content("02 / VALIDITY & CLASSROOM SYSTEM", "How NOVA works: from teacher setup to crew debrief", 3);
const steps = [
    ["01", "Teacher session", "The teacher picks a mission in Mission Control.", "step1"],
    ["02", "Crew roles", "Students join with one code and take a job.", "step2"],
    ["03", "Live emergency", "The crew shares power and air and repairs the ship.", "step3"],
    ["04", "Team debrief", "Everyone reviews what changed, and why.", "step4"]
];
steps.forEach(([number, head, body, image], index) => {
    const x = 0.5 + index * 2.3;
    card(slide, x, 1.4, 2.1, 2.55);
    slide.addImage({ path: img(image), x: x + 0.1, y: 1.5, w: 1.9, h: 1.07 });
    text(slide, number, { x: x + 0.15, y: 2.7, w: 0.5, h: 0.25, fontSize: 13, bold: true, color: index % 2 ? BLUE : ORANGE });
    text(slide, head, { x: x + 0.15, y: 2.98, w: 1.85, h: 0.25, fontSize: 12.5, bold: true });
    text(slide, body, { x: x + 0.15, y: 3.26, w: 1.85, h: 0.6, fontSize: 10, color: MUTED });
    if (index < 3)
        text(slide, "→", { x: x + 2.1, y: 2.5, w: 0.2, h: 0.3, fontSize: 12, color: MUTED, align: "center" });
});
card(slide, 0.5, 4.15, 9, 0.9, "EEF4FB");
text(slide, "THE RESEARCH", { x: 0.72, y: 4.28, w: 2, h: 0.18, fontSize: 8.5, color: BLUE, charSpacing: 1 });
text(slide, [
    { text: "Mining for Words", options: { italic: true, bold: true } },
    { text: " (2024): pupils who picked up English words by playing Minecraft improved more than pupils who studied the same words directly." }
], { x: 0.72, y: 4.5, w: 8.6, h: 0.45, fontSize: 11 });
slide.addNotes("What exactly is NOVA? NOVA is a system that allows teachers to create classroom sessions for their students. Using our modpack, students take part in realistic and plausible scenarios, learn how to deal with different challenges, and develop teamwork skills.\n\nCase study: \"Mining for Words\" (2024). This study showed that children who played Minecraft achieved relatively higher scores compared to those who learned in the traditional way.");

// 4 Scenario
slide = content("03 / LIVE MISSION PROTOTYPE", "In-game scenario: surviving a solar storm on Mars", 4);
slide.addImage({ path: img("storm"), x: 0.5, y: 1.4, w: 5.1, h: 2.87 });
text(slide, "Real gameplay: the crew has 29 seconds to reach the shelter", { x: 0.5, y: 4.36, w: 5.1, h: 0.2, fontSize: 9, color: MUTED, italic: true });
const beats = [
    ["1. A real hazard hits", "A solar storm alert, then a dust storm that starves the solar panels.", INK],
    ["2. The crew depends on each other", "Power, life support and weather officers each see different readings and share one budget.", BLUE],
    ["3. Skills that stick", "Teamwork, decisions under pressure and the science behind them.", INK]
];
beats.forEach(([head, body, color], index) => {
    const y = 1.4 + index * 1.03;
    card(slide, 5.85, y, 3.65, 0.9);
    text(slide, head, { x: 6.03, y: y + 0.13, w: 3.3, h: 0.24, fontSize: 12, bold: true, color });
    text(slide, body, { x: 6.03, y: y + 0.4, w: 3.3, h: 0.42, fontSize: 10, color: MUTED });
});
slide.addNotes("We built realistic missions based on real-life problems that have already occurred or are highly likely to happen in the future. Here the crew on Mars gets a solar storm warning and has to reach the shelter, then keep the outpost alive while a dust storm cuts their solar power.");

// 5 NASA data
slide = content("04 / NASA DATA", "Real NASA observations drive the mission", 5);
card(slide, 0.5, 1.4, 4.35, 3.6);
text(slide, "Opportunity rover: daily solar energy (Wh)", { x: 0.7, y: 1.55, w: 4, h: 0.22, fontSize: 11.5, bold: true });
const bars = [["2007 storm", 700, 128], ["2018 storm", 645, 21]];
bars.forEach(([label, before, peak], index) => {
    const x = 1.0 + index * 1.9, base = 4.05, scale = 1.55 / 700;
    slide.addShape(pres.ShapeType.rect, { x, y: base - before * scale, w: 0.55, h: before * scale, fill: { color: BLUE }, line: { color: BLUE, width: 0 } });
    slide.addShape(pres.ShapeType.rect, { x: x + 0.65, y: base - Math.max(0.03, peak * scale), w: 0.55, h: Math.max(0.03, peak * scale), fill: { color: ORANGE }, line: { color: ORANGE, width: 0 } });
    text(slide, String(before), { x, y: base - before * scale - 0.22, w: 0.55, h: 0.2, fontSize: 10, bold: true, align: "center" });
    text(slide, String(peak), { x: x + 0.65, y: base - Math.max(0.03, peak * scale) - 0.22, w: 0.55, h: 0.2, fontSize: 10, bold: true, align: "center" });
    text(slide, label, { x: x - 0.2, y: base + 0.06, w: 1.6, h: 0.2, fontSize: 9.5, color: MUTED, align: "center" });
});
slide.addShape(pres.ShapeType.rect, { x: 0.75, y: 1.9, w: 0.12, h: 0.12, fill: { color: BLUE }, line: { color: BLUE, width: 0 } });
text(slide, "Before the storm", { x: 0.93, y: 1.87, w: 1.3, h: 0.18, fontSize: 9, color: MUTED });
slide.addShape(pres.ShapeType.rect, { x: 2.3, y: 1.9, w: 0.12, h: 0.12, fill: { color: ORANGE }, line: { color: ORANGE, width: 0 } });
text(slide, "Storm peak", { x: 2.48, y: 1.87, w: 1.3, h: 0.18, fontSize: 9, color: MUTED });
text(slide, "In the game, sunlight drops to 18% (2007) or 3% (2018) at the storm peak.", { x: 0.7, y: 4.45, w: 4, h: 0.4, fontSize: 10, color: BLUE });
const sources = [
    ["Curiosity RAD, Sept 2017", "A solar storm doubled surface radiation", "Shelter-before-the-storm rule"],
    ["Apollo 13", "Power, oxygen and CO₂ removal", "Return Window rescue mission"],
    ["NASA Mars Fact Sheet", "Mars gravity is about 0.38 × Earth", "Low-gravity movement on Mars"]
];
sources.forEach(([head, body, use], index) => {
    const y = 1.4 + index * 1.03;
    card(slide, 5.1, y, 4.4, 0.9);
    text(slide, head, { x: 5.28, y: y + 0.11, w: 4.05, h: 0.22, fontSize: 11.5, bold: true });
    text(slide, body, { x: 5.28, y: y + 0.36, w: 4.05, h: 0.2, fontSize: 10, color: MUTED });
    text(slide, "→ " + use, { x: 5.28, y: y + 0.6, w: 4.05, h: 0.2, fontSize: 10, bold: true, color: ORANGE });
});
text(slide, "Sources: NASA Science, NASA JPL, NASA; 645 Wh from The Planetary Society. Measured values come from these sources; game timings and rates are our own teaching values.",
    { x: 5.1, y: 4.55, w: 4.4, h: 0.45, fontSize: 8, color: MUTED });
slide.addNotes("Thanks to the work of NASA's teams, we managed to create realistic missions based on real-life problems that have already occurred or are highly likely to happen in the future.\n\nFor example, the Mars mission's solar power follows the Opportunity rover's readings during the 2007 and 2018 dust storms.");

// 6 Differentiation
slide = content("05 / CREATIVITY & DIFFERENTIATION", "Others teach space. NOVA runs the mission.", 6);
const rivals = [
    ["Crater Crew", "Co-op browser game", "Mars and Moon strategy and exploration, not a crew fixing a spacecraft.", null],
    ["Artemis: Return to the Moon", "Minecraft Education × NASA", "Coding puzzles to fly the Orion capsule, not crew crisis operations.", "artemis"],
    ["NOVA", "Minecraft crew simulator", "Every student holds a crew role, and one decision affects the whole mission.", "nova"]
];
rivals.forEach(([head, kind, body, image], index) => {
    const x = 0.5 + index * 3.07, ours = index === 2;
    card(slide, x, 1.4, 2.86, 3.05, ours ? "EEF4FB" : WHITE);
    if (image)
        slide.addImage({ path: img(image), x: x + 0.1, y: 1.5, w: 2.66, h: 1.33 });
    else {
        slide.addShape(pres.ShapeType.rect, { x: x + 0.1, y: 1.5, w: 2.66, h: 1.33, fill: { color: "E9ECF2" }, line: { color: "E9ECF2", width: 0 } });
        text(slide, "Browser game", { x: x + 0.1, y: 2.02, w: 2.66, h: 0.3, fontSize: 12, color: MUTED, align: "center" });
    }
    text(slide, head, { x: x + 0.18, y: 2.98, w: 2.5, h: 0.26, fontSize: 13, bold: true, color: ours ? BLUE : INK });
    text(slide, kind, { x: x + 0.18, y: 3.26, w: 2.5, h: 0.2, fontSize: 9.5, color: MUTED });
    text(slide, body, { x: x + 0.18, y: 3.52, w: 2.5, h: 0.8, fontSize: 10.5 });
});
text(slide, "NOVA focuses on the mission itself and the problems astronauts face during it.", { x: 0.5, y: 4.65, w: 9, h: 0.3, fontSize: 13, bold: true, color: BLUE, align: "center" });
slide.addNotes("Similar applications.\n\nCrater Crew: a multiplayer educational game about Mars, designed for children and schools. It focuses more on strategy and exploration than on simulating a crew dealing with spacecraft problems.\n\nArtemis: Return to the Moon: a Minecraft Education project developed in partnership with NASA. Students work together, solve challenges, and use programming to navigate the Orion spacecraft.\n\nThe main difference between these projects and ours is that we focus on the actual missions and the challenges astronauts might face during them. Artemis: Return to the Moon, on the other hand, focuses mainly on programming challenges within a space-themed environment.");

// 7 Future
slide = content("06 / SCALABILITY & FUTURE VISION", "Scaling NOVA for classrooms everywhere", 7);
const future = [
    ["Expanded mission library", "More missions and scenarios on top of today's six.", "moon", false],
    ["Built into the game", "Classroom access modelled on Minecraft Education.", "mcedu", true],
    ["Accessible STEM training", "Astronaut training any school can start in one click.", "green", false]
];
future.forEach(([head, body, image, logo], index) => {
    const x = 0.5 + index * 3.07;
    card(slide, x, 1.4, 2.86, 2.55);
    if (logo) {
        slide.addShape(pres.ShapeType.rect, { x: x + 0.1, y: 1.5, w: 2.66, h: 1.33, fill: { color: WHITE }, line: { color: WHITE, width: 0 } });
        slide.addImage({ path: img(image), x: x + 0.23, y: 1.76, w: 2.4, h: 0.88 });
    } else
        slide.addImage({ path: img(image), x: x + 0.1, y: 1.5, w: 2.66, h: 1.33 });
    text(slide, head, { x: x + 0.18, y: 2.98, w: 2.5, h: 0.26, fontSize: 13, bold: true });
    text(slide, body, { x: x + 0.18, y: 3.27, w: 2.5, h: 0.55, fontSize: 10.5, color: MUTED });
});
slide.addImage({ path: img("logo"), x: 0.5, y: 4.2, w: 0.7, h: 0.7 });
text(slide, "Training the next generation of space crews, where students learn by playing together.", { x: 1.4, y: 4.22, w: 5.6, h: 0.5, fontSize: 13, bold: true });
text(slide, "github.com/KawaseDev/Nimbus-Nova-MC", { x: 1.4, y: 4.72, w: 5.6, h: 0.2, fontSize: 10, color: BLUE });
text(slide, "Screenshots are real captures of the mod. Code, docs and some textures were made with AI assistance. Not affiliated with NASA.",
    { x: 7.1, y: 4.25, w: 2.4, h: 0.65, fontSize: 7.5, color: MUTED });
slide.addNotes("In the future, we plan to expand NOVA by adding more missions and scenarios. Another possibility would be integrating our modpack directly into the main Minecraft game, similar to Minecraft Education, making it much more accessible to schools and students.\n\nThank you!");

pres.writeFile({ fileName: "NOVA-Pitch-v2.pptx" }).then(() => console.log("written"));
