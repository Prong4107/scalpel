if __name__ == "__main__":
    import unittest
    import tempfile
    from io import BytesIO
    from pyscalpel.http.body.form import (
        CaseInsensitiveDict,
        MultiPartFormField,
        MultiPartForm,
        Mapping,
        cast,
        QueryParams,
        JSON_KEY_TYPES,
        JSON_VALUE_TYPES,
        JSONForm,
        JSONFormSerializer,
        URLEncodedFormSerializer,
        multidict,
        os,
    )

    class MultiPartFormTestCase(unittest.TestCase):
        def setUp(self):
            headers = CaseInsensitiveDict(
                {
                    "Content-Disposition": 'form-data; name="file"; filename="example.txt"',
                    "Content-Type": "text/plain",
                }
            )
            content = b"This is the content of the file."
            encoding = "utf-8"
            self.form_field = MultiPartFormField(headers, content, encoding)
            self.form = MultiPartForm([self.form_field], "multipart/form-data")

        def test_mapping_interface(self):
            self.assertIsInstance(self.form, Mapping)

        def test_get_all(self):
            key = "file"
            expected_values = [self.form_field]
            values = self.form.get_all(key)
            self.assertEqual(values, expected_values)

        def test_get_all_empty_key(self):
            key = "nonexistent"
            expected_values = []
            values = self.form.get_all(key)
            self.assertEqual(values, expected_values)

        def test_get(self):
            key = "file"
            expected_value = self.form_field
            value = self.form.get(key)
            self.assertEqual(value, expected_value)

        def test_get_default(self):
            key = "nonexistent"
            default = MultiPartFormField.make("kjdsqkjdhdsqsq")
            expected_value = default
            value = self.form.get(key, default)
            self.assertEqual(value, expected_value)

        def test_del_all(self):
            key = "file"
            self.form.del_all(key)
            values = self.form.get_all(key)
            self.assertEqual(values, [])

        def test_del_all_empty_key(self):
            key = "nonexistent"
            self.form.del_all(key)  # No exception should be raised

        def test_delitem(self):
            key = "file"
            del self.form[key]
            values = self.form.get_all(key)
            self.assertEqual(values, [])

        # def test_delitem_key_error(self):
        #     key = "nonexistent"
        #     with self.assertRaises(KeyError):
        #         del self.form[key]

        def test_set(self):
            key = "new_file"
            value = MultiPartFormField.make(key)
            self.form[key] = value
            self.assertEqual(self.form.get(key), value)

        def test_set_bytes_value(self):
            key = "new_file"
            value = b"example content"
            self.form[key] = value
            form_field = cast(MultiPartFormField, self.form.get(key))
            self.assertEqual(form_field.name, key)
            self.assertEqual(form_field.content, value)

        def test_set_io_value(self):
            key = "new_file"
            value = BytesIO(b"example content")
            self.form[key] = value
            form_field = cast(MultiPartFormField, self.form.get(key))
            self.assertIsInstance(form_field, MultiPartFormField)
            self.assertEqual(form_field.name, key)
            self.assertEqual(form_field.content, b"example content")

        def test_set_none_value(self):
            key = "file"
            self.form[key] = None
            values = self.form.get_all(key)
            self.assertEqual(values, [])

        def test_set_default(self):
            key = "nonexistent"
            default = MultiPartFormField.make(key)
            value = self.form.setdefault(key, default)
            self.assertEqual(value, default)
            self.assertEqual(self.form.get(key), default)

        def test_set_default_existing(self):
            key = "file"
            default = MultiPartFormField.make(key)
            value = self.form.setdefault(key, default)
            self.assertEqual(value, self.form_field)
            self.assertEqual(self.form.get(key), self.form_field)

        def test_len(self):
            expected_length = 1
            length = len(self.form)
            self.assertEqual(length, expected_length)

        def test_iter(self):
            expected_fields = [self.form_field]
            fields = list(self.form)
            self.assertEqual(fields, expected_fields)

        def test_eq_same_fields(self):
            form2 = MultiPartForm([self.form_field], "multipart/form-data")
            self.assertEqual(self.form, form2)

        def test_eq_different_fields(self):
            form2 = MultiPartForm([], "multipart/form-data")
            self.assertNotEqual(self.form, form2)

        def test_items(self):
            expected_items = [("file", self.form_field)]
            items = list(self.form.items())
            self.assertEqual(items, expected_items)

        def test_values(self):
            expected_values = [self.form_field]
            values = list(self.form.values())
            self.assertEqual(values, expected_values)

        def test_keys(self):
            expected_keys = ["file"]
            keys = list(self.form.keys())
            self.assertEqual(keys, expected_keys)

        # def test_repr(self):
        #     expected_repr = "MultiPartForm[<MultiPartFormField(headers={'Content-Disposition': 'form-data; name=\"file\"; filename=\"example.txt\"', 'Content-Type': 'text/plain'}, content=b'This is the content of the file.', encoding='utf-8')>]"
        #     form_repr = repr(self.form)
        #     self.assertEqual(form_repr, expected_repr)

    class URLEncodedFormSerializerTestCase(unittest.TestCase):
        def test_serialize(self):
            serializer = URLEncodedFormSerializer()
            deserialized_body = multidict.MultiDict(
                ((b"name", b"John"), (b"age", b"30"))
            )
            expected = b"name=John&age=30"
            result = serializer.serialize(deserialized_body)
            self.assertEqual(result, expected)

        def test_deserialize(self):
            serializer = URLEncodedFormSerializer()
            body = b"name=John&age=30"
            expected = QueryParams([(b"name", b"John"), (b"age", b"30")])
            result = serializer.deserialize(body)
            self.assertEqual(result, expected)

        def test_deserialize_empty_body(self):
            serializer = URLEncodedFormSerializer()
            body = b""
            expected = QueryParams([])
            result = serializer.deserialize(body)
            self.assertEqual(result, expected)

        def test_get_empty_form(self):
            serializer = URLEncodedFormSerializer()
            expected = QueryParams([])
            result = serializer.get_empty_form()
            self.assertEqual(result, expected)

        def test_deserialized_type(self):
            serializer = URLEncodedFormSerializer()
            expected = QueryParams
            result = serializer.deserialized_type()
            self.assertEqual(result, expected)

        def test_import_form(self):
            exported_form = (
                ("key1", "value1"),
                ("key2", "value2"),
                ("key3", "value3"),
            )
            expected_fields = [
                ("key1", "value1"),
                ("key2", "value2"),
                ("key3", "value3"),
            ]
            serializer = URLEncodedFormSerializer()
            imported_form = serializer.import_form(exported_form)
            self.assertIsInstance(imported_form, QueryParams)
            items = list(imported_form.items())
            self.assertEqual(items, expected_fields)

        def test_export_form_to_tuple(self):
            form = QueryParams([(b"key1", b"value1"), (b"key2", b"value2")])
            serializer = URLEncodedFormSerializer()
            exported_form = serializer.export_form_to_tuple(form)
            expected_exported_form = ((b"key1", b"value1"), (b"key2", b"value2"))
            self.assertEqual(exported_form, expected_exported_form)

        def test_export_form_to_dict(self):
            form = QueryParams([(b"key1", b"value1"), (b"key2", b"value2")])
            serializer = URLEncodedFormSerializer()
            exported_form = serializer.export_form_to_dict(form)
            expected_exported_form = {"key1": "value1", "key2": "value2"}
            self.assertEqual(exported_form, expected_exported_form)

    class JSONFormSerializerTestCase(unittest.TestCase):
        def test_serialize(self):
            serializer = JSONFormSerializer()
            deserialized_body: dict[JSON_KEY_TYPES, JSON_VALUE_TYPES] = {
                "name": "John",
                "age": 30,
            }
            expected = b'{"name": "John", "age": 30}'
            result = serializer.serialize(deserialized_body)
            self.assertEqual(result, expected)

        def test_deserialize(self):
            serializer = JSONFormSerializer()
            body = b'{"name": "John", "age": 30}'
            expected = {"name": "John", "age": 30}
            result = serializer.deserialize(body)
            self.assertEqual(result, expected)

        def test_deserialize_empty_body(self):
            serializer = JSONFormSerializer()
            body = b""
            expected = None
            result = serializer.deserialize(body)
            self.assertEqual(result, expected)

        def test_get_empty_form(self):
            serializer = JSONFormSerializer()
            expected = JSONForm({})
            result = serializer.get_empty_form()
            self.assertEqual(result, expected)

        def test_deserialized_type(self):
            serializer = JSONFormSerializer()
            expected = JSONForm
            result = serializer.deserialized_type()
            self.assertEqual(result, expected)

    class MultiPartFormFieldTestCase(unittest.TestCase):
        def test_init(self):
            headers = CaseInsensitiveDict(
                {
                    "Content-Disposition": 'form-data; name="file"; filename="example.txt"',
                    "Content-Type": "text/plain",
                }
            )
            content = b"This is the content of the file."
            encoding = "utf-8"
            expected_headers = CaseInsensitiveDict(
                {
                    "Content-Disposition": 'form-data; name="file"; filename="example.txt"',
                    "Content-Type": "text/plain",
                }
            )
            expected_content = b"This is the content of the file."
            expected_encoding = "utf-8"
            result = MultiPartFormField(headers, content, encoding)
            self.assertEqual(result.headers, expected_headers)
            self.assertEqual(result.content, expected_content)
            self.assertEqual(result.encoding, expected_encoding)

        def test_file_upload(self):
            with tempfile.NamedTemporaryFile(delete=False) as temp_file:
                temp_file.write(b"This is the content of the file.")

            filename = temp_file.name
            content_type = "text/plain"

            form_field = MultiPartFormField.from_file(
                "file", filename, content_type=content_type
            )

            self.assertEqual(form_field.name, "file")
            self.assertEqual(form_field.filename, os.path.basename(filename))
            self.assertEqual(form_field.content_type, content_type)
            self.assertEqual(form_field.content, b"This is the content of the file.")

            os.remove(filename)

    unittest.main()
