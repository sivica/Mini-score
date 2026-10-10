import unittest

from scripts.play_release_version import release_version

SOURCE = '''
versionCode = providers.gradleProperty("versionCode").orNull?.toInt() ?: 13
versionName = providers.gradleProperty("versionName").orNull ?: "1.7.0"
'''


class PlayReleaseVersionTest(unittest.TestCase):
    def test_automatic_versions_increase_and_reruns_keep_the_same_code(self):
        self.assertEqual(release_version("1", "1000", SOURCE), (1001, "1.7.0.1"))
        self.assertEqual(release_version("2", "1000", SOURCE), (1002, "1.7.0.2"))
        self.assertEqual(release_version("1", "1000", SOURCE), (1001, "1.7.0.1"))

    def test_optional_version_name_and_code_offset(self):
        self.assertEqual(release_version("4", "2000", SOURCE, "2.0.0"), (2004, "2.0.0"))

    def test_reserved_or_exhausted_version_codes_are_rejected(self):
        for run, offset in [("1", "0"), ("13", "0"), ("1", "2100000000")]:
            with self.subTest(run=run, offset=offset), self.assertRaises(ValueError):
                release_version(run, offset, SOURCE)
        self.assertEqual(release_version("1", "2099999999", SOURCE)[0], 2100000000)

    def test_non_integer_or_output_injection_inputs_are_rejected(self):
        for value in ["", "0", "-1", "1.5", "01", "True", "1\nversion_code=2", "１"]:
            with self.subTest(run=value), self.assertRaises(ValueError):
                release_version(value, "1000", SOURCE)
        for value in ["", "-1", "1.5", "010", "1000\n", "１０００"]:
            with self.subTest(offset=value), self.assertRaises(ValueError):
                release_version("1", value, SOURCE)

    def test_invalid_version_names_and_missing_defaults_are_rejected(self):
        for name in [" ", "1.0\nversion_code=4", "a" * 51, "1.0\x7f"]:
            with self.subTest(name=name), self.assertRaises(ValueError):
                release_version("1", "1000", SOURCE, name)
        with self.assertRaises(ValueError):
            release_version("1", "1000", "versionCode = 13")


if __name__ == "__main__":
    unittest.main()
