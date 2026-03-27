-- V7: NSE market holiday calendar 2025–2026
--
-- Source: NSE official holiday list (https://www.nseindia.com/resources/exchange-communication-holidays)
-- Annual refresh: Each December, verify next-year dates and insert a new versioned migration.
-- Lunar-calendar holidays (Id-Ul-Fitr, Holi, Muharram) may shift ±1 day; confirm before each year-end.
--
-- ON CONFLICT DO NOTHING makes this migration re-runnable if the table already has data.

INSERT INTO market_holidays (holiday_date, description) VALUES
    ('2025-02-26', 'Mahashivratri'),
    ('2025-03-14', 'Holi'),
    ('2025-03-31', 'Id-Ul-Fitr (Ramzan Id)'),
    ('2025-04-14', 'Dr. Baba Saheb Ambedkar Jayanti'),
    ('2025-04-18', 'Good Friday'),
    ('2025-05-01', 'Maharashtra Day'),
    ('2025-05-12', 'Buddha Purnima'),
    ('2025-08-15', 'Independence Day'),
    ('2025-08-27', 'Ganesh Chaturthi'),
    ('2025-10-02', 'Mahatma Gandhi Jayanti / Dussehra'),
    ('2025-10-20', 'Diwali — Laxmi Puja'),
    ('2025-10-21', 'Diwali — Balipratipada'),
    ('2025-11-05', 'Gurunanak Jayanti'),
    ('2025-12-25', 'Christmas')
ON CONFLICT (holiday_date) DO NOTHING;

INSERT INTO market_holidays (holiday_date, description) VALUES
    ('2026-01-26', 'Republic Day'),
    ('2026-02-17', 'Mahashivratri'),
    ('2026-03-03', 'Holi'),
    ('2026-03-20', 'Id-Ul-Fitr (Ramzan Id)'),
    ('2026-04-03', 'Good Friday'),
    ('2026-04-14', 'Dr. Baba Saheb Ambedkar Jayanti'),
    ('2026-05-01', 'Maharashtra Day'),
    ('2026-05-27', 'Buddha Purnima'),
    ('2026-10-02', 'Mahatma Gandhi Jayanti'),
    ('2026-11-09', 'Gurunanak Jayanti'),
    ('2026-12-25', 'Christmas')
ON CONFLICT (holiday_date) DO NOTHING;
