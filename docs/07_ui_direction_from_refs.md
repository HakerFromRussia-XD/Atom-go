# UI Direction from References

## 1. Общий визуальный стиль
- Светлая тема по умолчанию.
- Чистые экраны с большим количеством воздуха.
- Контрастные заголовки и аккуратные secondary-тексты.
- Простые крупные touch-цели.

## 2. Базовая палитра (MVP)
- Background: `#FFFFFF`
- Primary text: `#111111`
- Secondary text: `#6B6B6B`
- Divider: `#EDEDED`
- Accent orange (из login reference): `#E28A00`
- Accent green (из eco reference): `#08A74E`
- Danger red (для долга и destructive actions): `#E53935`
- Success green (для прибыли): `#22A447`

## 3. Компонентный язык
- Карточки с мягким радиусом 12-20.
- Bottom sheet для подтверждений/опасных действий.
- Строки-ячейки списков: avatar + title + subtitle + action/value справа.
- Большие кнопки для платежных сценариев.

## 4. Экраны MVP
- Login screen (минималистичный, с четким CTA).
- Client dashboard (модель велосипеда, paid_until, долг, быстрые суммы).
- Admin client list (баланс/прибыль справа, цветовые статусы).
- Admin client details (профиль + история аренд).
- Payment flow (выбор суммы, подтверждение, статус).

## 5. UX правила
- Все деньги в рублях, формат `12 340 ₽`.
- Любые операции корректировки долга требуют комментарий.
- Для удаляющих/опасных действий использовать confirmation bottom sheet.
