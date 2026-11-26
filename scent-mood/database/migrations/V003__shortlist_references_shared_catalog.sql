-- The finder and catalog now share the bundled JSON. Existing saves retain their stable IDs.
ALTER TABLE shortlist_item DROP CONSTRAINT shortlist_item_fragrance_id_fkey;
