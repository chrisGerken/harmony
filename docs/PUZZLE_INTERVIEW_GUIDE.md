# Puzzle Interview Guide for Chatbots

This document describes how to interview a user to create a Harmony puzzle specification file (`interview.txt`) using the BOARD and MOVES format.

## Overview

The goal is to gather information from the user about their puzzle and write a valid puzzle specification file that can be solved by the Harmony Puzzle Solver.

## Output File Format

The interview will produce a file named `interview.txt` with this structure:

```
ROWS <number>
COLS <number>

BOARD
<color1> <pos1> <moves1> <pos2> <moves2> ...
<color2> <pos1> <moves1> <pos2> <moves2> ...
...

MOVES
<move1>
<move2>
...
```

## Interview Questions

Ask these questions in order:

### Step 1: Board Dimensions

**Question 1:** "How many rows does your puzzle have?"
- Accept a positive integer (typically 2-6)
- Store as `ROWS`

**Question 2:** "How many columns does your puzzle have?"
- Accept a positive integer (typically 2-6)
- Store as `COLS`

### Step 2: Color Names

**Question 3:** "What are the color names for each row? List them in order from top row (Row A) to bottom."
- The number of colors MUST equal the number of rows
- Each color becomes the target for its corresponding row (first color = Row A target, etc.)
- Examples: RED, BLUE, GREEN, YELLOW, ORANGE, PURPLE, PINK, BROWN
- Colors are case-insensitive (will be stored uppercase)
- Store as array `COLORS[]`

#### Handling Multi-Word Color Names

Users may provide multi-word color names like "dark red", "light green", "sky blue", etc. These MUST be normalized to single-word abbreviated format:

**Abbreviation Rules:**
1. Convert to uppercase
2. Abbreviate common prefixes:
   - "DARK" → "DK"
   - "LIGHT" → "LT"
   - "MEDIUM" → "MD"
   - "PALE" → "PL"
   - "BRIGHT" → "BR"
   - "DEEP" → "DP"
   - "SKY" → "SK"
   - "FOREST" → "FR"
   - "NAVY" → "NV"
   - "ROYAL" → "RL"
   - "HOT" → "HT"
   - "LIME" → "LM"
   - "OLIVE" → "OL"
   - "STEEL" → "ST"
3. Concatenate the abbreviated prefix with the base color (no spaces)

**Examples:**
| User Input | Normalized Color |
|------------|------------------|
| dark red | DKRED |
| light green | LTGREEN |
| sky blue | SKBLUE |
| forest green | FRGREEN |
| hot pink | HTPINK |
| navy blue | NVBLUE |
| deep purple | DPPURPLE |
| medium gray | MDGRAY |
| pale yellow | PLYELLOW |
| bright orange | BRORANGE |
| royal blue | RLBLUE |
| olive green | OLGREEN |
| steel blue | STBLUE |
| lime green | LMGREEN |

**For unrecognized prefixes:** Use the first two letters of the prefix word.
- "dusty rose" → "DUROSE"
- "burnt orange" → "BUORANGE"

**Important:** When referencing the color later (for tile colors), accept EITHER the original multi-word input OR the abbreviated form. Store and write only the abbreviated form.

### Step 3: Tile Information

For each position on the board, gather the tile's color and remaining moves.

**Explain the coordinate system:**
- Rows are labeled A, B, C, D... (top to bottom)
- Columns are labeled 1, 2, 3, 4... (left to right)
- Position A1 is top-left, A2 is next to the right, B1 is below A1

**For each position (row-by-row, left-to-right):**

**Question 4 (repeated):** "For position [X#], what color is the tile and how many remaining moves does it have?"
- Accept: `<color> <moves>` (e.g., "RED 3" or "BLUE 0" or "dark red 2")
- Color must be one of the defined colors from Step 2 (accept original or abbreviated form)
- Moves must be a non-negative integer (0-9 typical)
- Store in `TILES` map: position -> (color, moves)
- Always store colors in their abbreviated/normalized form

**Shortcut option:** If the user wants to describe the board more efficiently, accept:
- "Row A: RED 2, BLUE 1, GREEN 3" (colors and moves for each column in order)
- "All RED with 0 moves" (for rows that are already solved)

### Step 4: Moves (Optional)

**Question 5:** "Do you want to apply any moves to transform this board into a different starting state? (yes/no)"

If yes:

**Question 6 (repeated):** "Enter a move in the format Position1-Position2 (e.g., A1-B1 for vertical swap, A1-A3 for horizontal swap), or 'done' when finished."
- Moves must be between tiles in the same row OR same column
- Horizontal swap example: A1-A3 (same row A, columns 1 and 3)
- Vertical swap example: A1-B1 (same column 1, rows A and B)
- Store moves in order in `MOVES[]` array

## Validation Rules

Before writing the file, validate:

1. **Dimension check:** ROWS and COLS are positive integers
2. **Color count:** Number of colors equals ROWS
3. **Tile count:** Number of tiles equals ROWS × COLS
4. **Color validity:** Each tile's color is in the COLORS list (match both original and abbreviated forms)
5. **Move validity:** Each move connects tiles in the same row OR same column
6. **Position bounds:** All positions are within the board (A-? for rows, 1-? for columns)
7. **No duplicate positions:** Each position is specified exactly once

## Writing interview.txt

Once all information is gathered and validated, write the file:

```
# Harmony Puzzle - Created via Interview
# Dimensions: {ROWS}x{COLS}

ROWS {ROWS}
COLS {COLS}

BOARD
{For each color in COLORS[]:}
{COLOR} {pos1} {moves1} {pos2} {moves2} ...
{... list all tiles of this color with their positions and moves}

{If MOVES is not empty:}
MOVES
{move1}
{move2}
...
```

### BOARD Section Format Details

For each color (in the order they were defined):
1. Start the line with the color name (use the ABBREVIATED form)
2. Find ALL tiles on the board that have this color
3. For each such tile, append: ` <position> <moves>`
4. Positions are formatted as letter+number (e.g., A1, B3, C2)

**Example:** If DKRED tiles are at A1 (3 moves), B2 (1 move), and C3 (2 moves):
```
DKRED A1 3 B2 1 C3 2
```

## Complete Example Interview

**Bot:** How many rows does your puzzle have?
**User:** 3

**Bot:** How many columns does your puzzle have?
**User:** 3

**Bot:** What are the color names for each row? List them in order from top row (Row A) to bottom.
**User:** dark red, light green, sky blue

**Bot:** I'll normalize those color names:
- "dark red" → DKRED (Row A target)
- "light green" → LTGREEN (Row B target)
- "sky blue" → SKBLUE (Row C target)

**Bot:** I'll now ask about each tile. Rows are A, B, C (top to bottom). Columns are 1, 2, 3 (left to right).

**Bot:** For position A1, what color is the tile and how many remaining moves?
**User:** light green 2

**Bot:** For position A2, what color is the tile and how many remaining moves?
**User:** DKRED 1

*... continues for all 9 positions ...*

**Bot:** Do you want to apply any moves to transform this board?
**User:** yes

**Bot:** Enter a move (e.g., A1-B1), or 'done' when finished.
**User:** A1-A2

**Bot:** Enter a move, or 'done'.
**User:** done

### Resulting interview.txt:

```
# Harmony Puzzle - Created via Interview
# Dimensions: 3x3

ROWS 3
COLS 3

BOARD
DKRED A2 1 B1 0 C3 2
LTGREEN A1 2 B2 1 C1 0
SKBLUE A3 0 B3 3 C2 1

MOVES
A1-A2
```

## Color Name Reference Table

Keep this mapping to accept user input in either form:

| Original Input | Abbreviated Form |
|----------------|------------------|
| dark red | DKRED |
| dark blue | DKBLUE |
| dark green | DKGREEN |
| light red | LTRED |
| light blue | LTBLUE |
| light green | LTGREEN |
| sky blue | SKBLUE |
| forest green | FRGREEN |
| navy blue | NVBLUE |
| hot pink | HTPINK |
| deep purple | DPPURPLE |
| royal blue | RLBLUE |
| bright orange | BRORANGE |
| pale yellow | PLYELLOW |
| medium gray | MDGRAY |
| olive green | OLGREEN |
| steel blue | STBLUE |
| lime green | LMGREEN |

For single-word colors (RED, BLUE, GREEN, etc.), use them as-is in uppercase.

## Error Handling

If validation fails, explain the error and ask the user to correct:

- "The number of colors (4) doesn't match the number of rows (3). Please provide exactly 3 colors."
- "Position D1 is out of bounds for a 3-row board. Rows are A, B, C."
- "Move A1-B2 is invalid. Tiles must be in the same row or same column."
- "Color 'PURPLE' was not defined. Available colors are: DKRED, LTGREEN, SKBLUE"
- "Position A1 was specified twice. Each position should appear exactly once."

## Tips for the Interviewing Chatbot

1. **Be patient:** Users may not be familiar with the notation
2. **Offer examples:** Show sample inputs when asking questions
3. **Confirm understanding:** Summarize the board after all tiles are entered
4. **Allow corrections:** Let users go back and fix mistakes
5. **Show progress:** Display a visual board representation as tiles are entered
6. **Validate early:** Check each input immediately rather than waiting until the end
7. **Accept flexible color input:** Always accept both the original multi-word color name AND the abbreviated form when the user specifies tile colors
8. **Show the mapping:** When multi-word colors are provided, immediately show the user the abbreviated form that will be used
