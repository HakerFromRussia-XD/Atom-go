# UI Scaling Rules (Mandatory)

## Core rule
All iPhone screens must be built from a **base reference layout** and scaled proportionally to the current device size.

Rule text (fixed with product owner):
"Перевожу экран на масштаб от базового референса (пропорционально размеру экрана), чтобы вид был одинаково близким на разных iPhone."

## Implementation requirement
- Use one shared scale formula in every screen (`min(screenWidth/baseWidth, screenHeight/baseHeight)`).
- Do not hardcode unrelated fixed sizes per model.
- Keep visual hierarchy, spacing, and proportions close to the approved reference.

## Current base for login screen
- Base reference size: `600 x 1260`.
- Scale formula: `min(screenWidth/baseWidth, screenHeight/baseHeight)`.

## Apply to all next iOS screens
- Client Home
- Admin Home
- Lists / cards / detail pages
- All auth and settings screens
