import pytest
from tests import marks
from tests.base_test_case import MultipleDeviceTestCase
from views.sign_in_view import SignInView


@marks.chat
class TestPublicChat(MultipleDeviceTestCase):

    @marks.testrail_id(1383)
    def test_public_chat_messaging(self):
        self.create_drivers(2)
        device_1, device_2 = SignInView(self.drivers[0]), SignInView(self.drivers[1])
        username_1, username_2 = 'user_1', 'user_2'
        home_1, home_2 = device_1.create_user(username=username_1), device_2.create_user(username=username_2)
        public_key_2 = home_2.get_public_key()
        home_2.home_button.click()

        home_1.add_contact(public_key_2)
        home_1.get_back_to_home_view()

        public_chat_name = home_1.get_public_chat_name()
        chat_1, chat_2 = home_1.join_public_chat(public_chat_name), home_2.join_public_chat(public_chat_name)

        message = 'hello'
        chat_1.chat_message_input.send_keys(message)
        chat_1.send_message_button.click()

        chat_2.verify_message_is_under_today_text(message, self.errors)
        if chat_2.chat_element_by_text(message).username.text != username_1:
            self.errors.append("Username '%s' is not shown next to the received message" % username_1)

        if chat_1.element_by_text(username_1).is_element_displayed():
            self.errors.append("Username '%s' is shown for the sender" % username_1)

        self.verify_no_errors()

    @marks.testrail_id(3706)
    def test_public_chat_clear_history(self):
        self.create_drivers(2)
        device_1, device_2 = SignInView(self.drivers[0]), SignInView(self.drivers[1])
        chat_name = device_1.get_public_chat_name()
        for sign_in in device_1, device_2:
            home = sign_in.create_user()
            home.join_public_chat(chat_name)
        chat_1, chat_2 = device_1.get_chat_view(), device_2.get_chat_view()
        message_1, message_2, message_3 = 'm1', 'm2', 'm3'
        chat_1.chat_message_input.send_keys(message_1)
        chat_1.send_message_button.click()
        chat_2.element_by_text(message_1).is_element_present()

        chat_2.chat_message_input.send_keys(message_2)
        chat_2.send_message_button.click()
        chat_1.element_by_text(message_2).is_element_present()
        chat_1.chat_options.click()
        chat_1.clear_history_button.click()
        chat_1.clear_button.click()
        chat_2.chat_message_input.send_keys(message_3)
        chat_2.send_message_button.click()
        chat_1.element_by_text(message_3).is_element_present()
        for message in message_1, message_2:
            if chat_1.element_starts_with_text(message).is_element_present():
                pytest.fail("Message '%s' is shown, but public chat history has been cleared" % message)
        home_1 = chat_1.get_back_to_home_view()
        home_1.relogin()
        home_1.element_by_text('#' + chat_name).click()
        for message in message_1, message_2:
            if chat_1.element_starts_with_text(message).is_element_present():
                pytest.fail("Message '%s' is shown after re-login, but public chat history has been cleared" % message)
