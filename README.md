# Ivana School Prep (Android)

One app for Ivana's Grade 4 practice, with a home screen of subjects and a weekly test for each.
It is the Reading Coach app renamed (same app id, so it updates in place):

- **Reading Coach**: reading comprehension and grammar for the Wonders tests (details below).
- **Greek & Latin**: root lists by unit, with the same 4-level ladder, scoring, cheers, test mode and
  "Fix my mistakes" as grammar. Level 1 Roots (root ↔ meaning, 8/10) → 2 Words (word meanings, which
  word has the root, 9/10) → 3 Tricky (words in sentences, choose two, 9/10) → 4 Test day (12 questions,
  test mode, 11 right). "Study the roots" lists every root with example words; any unit can be practiced
  on its own. Roots live in `GL_UNITS` in `index.html`; questions are generated from them. The starter
  units are common Grade 4 roots: replace or add units from the class lists (newest unit last).
- **Mad Minute**: the Mad Minute drill (30 facts, 1/1½/2-minute timer, tough ones, 2-digit box
  method), embedded from the Mad Minute app and running in its own frame. Its page lives inside the
  `<template id="mmTpl">` block. The standalone Mad Minute app still works on its own.
- **This week's tests** (home screen): Reading test (2 stories, 12 questions), Grammar test (12),
  Greek & Latin quiz (10) and Mad Minute (30 facts, 1 minute, all facts). Test mode, like the
  Wednesday tests at school. The first finished try each week (Monday to Sunday) is the grade;
  retakes are practice. Grades use the school scale (A 90+, B 80+, C 70+, D 60+, with + and −).
- **Weekly report card**: one column per week: each test's grade, the week's grade (average of the
  tests taken), practice accuracy per subject, best 1-minute Mad Minute, levels unlocked, rounds practiced.

## Reading Coach

Aimed at her two low Unit 1 scores:
the U1 W1-2 comprehension test (70%) and the U1 W4 grammar test (60%).

- **Practice test**: an original story with numbered paragraphs + 6 test-style questions
  (multiple choice, Part A / Part B text evidence, "choose two"). 12 stories (4 per level): realistic
  fiction, folktales, biographies, expository texts.
- **Grammar drill**: 10 questions, mixed or by topic, following Wonders Unit 1:
  kinds of sentences, subjects & predicates, compound, complex, run-ons.
- **Levels** (reading and grammar each have 4). Pass a level twice to unlock the next:
  - Reading: 1 Warm-up (easier stories, paragraph highlighted) → 2 Stretch (harder stories, no
    highlight) → 3 Challenge (hardest stories, test mode: answers shown only at the end) →
    4 Test day (two stories, 12 questions, 11 right to pass).
  - Grammar: 1 Basics (8/10) → 2 Combine (9/10) → 3 Tricky: proofreading and comma traps (9/10) →
    4 Test day (12 mixed questions, test mode, 11 right to pass).
  - Lower levels can be replayed any time; only the highest unlocked level counts toward a pass.
- **Learn**: 17 short lessons (5 grammar topics + 12 reading skills). Each has the rule in kid
  words, color-coded examples, an "On the test" tip, 2 try-it questions, and a button to practice
  that exact skill. Weakest skills are listed first. After a round, the tip card links to the lesson.
- **Cheers**: right answers get a happy chime, spoken praise ("Great job!", "You're awesome!"), a
  pop-up bubble and sometimes confetti. Streaks of 3/5/8/10 get bigger cheers. Wrong answers get a
  gentle "Nice try!" instead of a buzzer. In test mode, she gets effort cheers ("Halfway there!",
  "Last one!") without giving away answers. Finishing plays a fanfare. Sounds and Voice can be
  switched off on the home screen. The voice uses the phone's text-to-speech (app v1.1).
- **Fix my mistakes**: every missed question comes back until she gets it right twice.
- Every answer shows why; each round ends with a tip card for the skill she missed most.
- Progress (scores, accuracy per skill) is saved on the phone.

## Adding or changing questions
Everything lives in `app/src/main/assets/index.html` (`GRAMMAR`, `STORIES`, `TIPS`, `GL_UNITS` near the
top of the script). Once it is on `main`, the app downloads it on next launch. No reinstall.

## Get the APK
Pushes that change the app code run the "Build Reading Coach APK" workflow (the file keeps its old name) (`.github/workflows/apk.yml`):

https://github.com/devsenweb/reading-coach/releases/download/latest/ReadingCoach.apk

Open that on the Android phone, tap the download, and allow "Install unknown apps" when asked.
It installs alongside Mad Minute (different app id).
