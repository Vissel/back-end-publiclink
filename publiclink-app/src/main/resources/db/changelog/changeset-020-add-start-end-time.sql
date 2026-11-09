-- Changeset 020: Add start_time and end_time columns to request table
-- Author: thach
-- Date: 2026

-- Add start_time column
ALTER TABLE request
    ADD COLUMN start_time TIMESTAMP;

-- Add end_time column  
ALTER TABLE request
    ADD COLUMN end_time TIMESTAMP;