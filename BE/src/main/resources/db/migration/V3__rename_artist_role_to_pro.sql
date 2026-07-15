UPDATE roles
   SET name = 'PRO',
       description = 'Professional account with premium features'
 WHERE name = 'ARTIST';

UPDATE roles
   SET description = 'Standard account with basic access'
 WHERE name = 'USER';

UPDATE roles
   SET description = 'System administrator with full access'
 WHERE name = 'ADMIN';