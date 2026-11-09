-- Create business_rule table
CREATE TABLE business_rule (
    id INT AUTO_INCREMENT PRIMARY KEY,
    fee DECIMAL(15,2) NOT NULL,
    day INT NOT NULL,
    description VARCHAR(255) NULL
);

-- Insert initial data
INSERT INTO business_rule (fee, day, description) VALUES
(3000.00, 1, '1 environment in the same time'),
(10000.00, 7, '1 environment in the same time'),
(15000.00, 7, '3 environment in the same time'),
(15000.00, 14, '1 environment in the same time'),
(20000.00, 14, '3 environment in the same time'),
(20000.00, 30, '1 environment in the same time'),
(25000.00, 30, '3 environment in the same time');