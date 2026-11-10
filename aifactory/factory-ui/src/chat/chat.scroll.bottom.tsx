import { FloatButton } from 'antd'
import { ArrowDownOutlined } from '@ant-design/icons'

import { content } from '../shared/content'

interface Props {
  /** to the end of the log, and the follow is armed again (UC-WP-80) */
  onFollow(): void
}

/**
 * The way back to the end of the log (R-WP-35, UC-WP-80): an arrow that floats, says "Scroll bottom" and
 * re-arms the follow it answers to. Who is on the screen is the caller's decision — the basis' own `BackTop`
 * keeps that question in its own scroll listener, while our reading position is `useFollowBottom`'s, and one
 * answer may not live twice. Where it stands is the caller's sheet too: the basis would fix it to the window
 * and drop it onto the send button of the composer below.
 */
export function ScrollBottomButton({ onFollow }: Props) {
  return (
    <FloatButton
      aria-label={content.chat.scrollBottom}
      classNames={{ root: 'chat-scroll-bottom-btn' }}
      data-testid="scroll-bottom"
      icon={<ArrowDownOutlined />}
      tooltip={content.chat.scrollBottom}
      onClick={onFollow}
    />
  )
}
