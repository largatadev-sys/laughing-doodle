-- Placeholder identities and a single shared placeholder password (BCrypt hash of
-- "changeme123"), never a real credential. Real passwords are set later via a manual
-- DB update (the admin-reset mechanism from ADR-002).
INSERT INTO users (name, username, password_hash, role) VALUES
    ('Admin One',  'admin',   '$2a$10$9f1BL/OiJ5NBDm04Zs9eZeDwZiCTYVkYp1yIXfYNoX5WztG9fwD66', 'admin'),
    ('Member One', 'member1', '$2a$10$9f1BL/OiJ5NBDm04Zs9eZeDwZiCTYVkYp1yIXfYNoX5WztG9fwD66', 'member'),
    ('Member Two', 'member2', '$2a$10$9f1BL/OiJ5NBDm04Zs9eZeDwZiCTYVkYp1yIXfYNoX5WztG9fwD66', 'member'),
    ('Member Three', 'member3', '$2a$10$9f1BL/OiJ5NBDm04Zs9eZeDwZiCTYVkYp1yIXfYNoX5WztG9fwD66', 'member');
