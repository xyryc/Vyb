/**
 * Vyb Privacy Policy Website - Core Application Script
 * Developer: Stonewell Studio
 */

document.addEventListener('DOMContentLoaded', () => {
  initTheme();
  initTableOfContents();
  initFAQs();
  initSearch();
  initPrint();
  initEqualizer();
});

/* ==========================================================================
   1. Theme Management (Light/Dark Mode)
   ========================================================================== */
function initTheme() {
  const themeToggleBtn = document.getElementById('theme-toggle');
  const body = document.body;

  // Retrieve saved theme or use system preference (default to dark)
  const savedTheme = localStorage.getItem('theme');
  const systemPrefersLight = window.matchMedia('(prefers-color-scheme: light)').matches;

  if (savedTheme === 'light' || (!savedTheme && systemPrefersLight)) {
    setTheme('light');
  } else {
    setTheme('dark');
  }

  themeToggleBtn.addEventListener('click', () => {
    if (body.classList.contains('dark-theme')) {
      setTheme('light');
    } else {
      setTheme('dark');
    }
  });

  function setTheme(theme) {
    if (theme === 'light') {
      body.classList.remove('dark-theme');
      body.classList.add('light-theme');
      localStorage.setItem('theme', 'light');
    } else {
      body.classList.remove('light-theme');
      body.classList.add('dark-theme');
      localStorage.setItem('theme', 'dark');
    }
  }
}

/* ==========================================================================
   2. Table of Contents & Scroll Spy
   ========================================================================== */
function initTableOfContents() {
  const sections = document.querySelectorAll('.policy-section');
  const tocLinks = document.querySelectorAll('.toc-link');

  if (sections.length === 0 || tocLinks.length === 0) return;

  // Use IntersectionObserver to track scroll intersection
  const observerOptions = {
    root: null, // Viewport
    rootMargin: '-20% 0px -60% 0px', // Trigger near middle of screen
    threshold: 0
  };

  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        const id = entry.target.getAttribute('id');
        
        // Remove active class from all links
        tocLinks.forEach(link => {
          link.classList.remove('active');
          if (link.getAttribute('href') === `#${id}`) {
            link.classList.add('active');
          }
        });
      }
    });
  }, observerOptions);

  sections.forEach(section => {
    observer.observe(section);
  });

  // Smooth scroll target adjustment for anchors to account for header height
  tocLinks.forEach(link => {
    link.addEventListener('click', (e) => {
      e.preventDefault();
      const targetId = link.getAttribute('href');
      const targetSection = document.querySelector(targetId);
      
      if (targetSection) {
        const headerOffset = 100;
        const elementPosition = targetSection.getBoundingClientRect().top;
        const offsetPosition = elementPosition + window.pageYOffset - headerOffset;

        window.scrollTo({
          top: offsetPosition,
          behavior: 'smooth'
        });
        
        // Set URL hash without scrolling automatically
        history.pushState(null, null, targetId);
      }
    });
  });
}

/* ==========================================================================
   4. FAQ Accordion Dropdowns
   ========================================================================== */
function initFAQs() {
  const triggers = document.querySelectorAll('.accordion-trigger');

  triggers.forEach(trigger => {
    trigger.addEventListener('click', () => {
      const isExpanded = trigger.getAttribute('aria-expanded') === 'true';
      const content = document.getElementById(trigger.getAttribute('aria-controls'));

      // Close all other accordions first for a clean accordion flow
      triggers.forEach(otherTrigger => {
        if (otherTrigger !== trigger && otherTrigger.getAttribute('aria-expanded') === 'true') {
          otherTrigger.setAttribute('aria-expanded', 'false');
          const otherContent = document.getElementById(otherTrigger.getAttribute('aria-controls'));
          otherContent.style.maxHeight = null;
        }
      });

      // Toggle current accordion
      if (isExpanded) {
        trigger.setAttribute('aria-expanded', 'false');
        content.style.maxHeight = null;
      } else {
        trigger.setAttribute('aria-expanded', 'true');
        content.style.maxHeight = content.scrollHeight + 'px';
      }
    });
  });
}

/* ==========================================================================
   5. Client-side Privacy Policy Search
   ========================================================================== */
function initSearch() {
  const searchInput = document.getElementById('policy-search');
  const clearBtn = document.getElementById('clear-search');
  const sections = document.querySelectorAll('.policy-section');
  const statusContainer = document.getElementById('search-status');

  searchInput.addEventListener('input', () => {
    const query = searchInput.value.toLowerCase().trim();
    
    // Toggle clear button visibility
    if (query.length > 0) {
      clearBtn.classList.remove('hidden');
    } else {
      clearBtn.classList.add('hidden');
      resetSearch();
      return;
    }

    let matchCount = 0;

    sections.forEach(section => {
      // Clean previous highlights to prevent nested tags
      removeHighlights(section);

      const contentText = section.textContent.toLowerCase();
      const hasMatch = contentText.includes(query);

      if (hasMatch) {
        section.style.display = 'flex';
        matchCount++;
        // Apply text highlights on the text nodes
        highlightText(section, query);
      } else {
        section.style.display = 'none';
      }
    });

    // Update search status message
    statusContainer.classList.remove('hidden');
    if (matchCount === 0) {
      statusContainer.innerHTML = `No results found for "<strong>${escapeHtml(query)}</strong>". Try checking for typos.`;
    } else {
      statusContainer.innerHTML = `Found <strong>${matchCount}</strong> matching section${matchCount > 1 ? 's' : ''} for "<strong>${escapeHtml(query)}</strong>".`;
    }
  });

  clearBtn.addEventListener('click', () => {
    searchInput.value = '';
    clearBtn.classList.add('hidden');
    resetSearch();
    searchInput.focus();
  });

  function resetSearch() {
    sections.forEach(section => {
      section.style.display = 'flex';
      removeHighlights(section);
    });
    statusContainer.classList.add('hidden');
    statusContainer.innerHTML = '';
  }

  function highlightText(element, query) {
    const walk = document.createTreeWalker(element, NodeFilter.SHOW_TEXT, null, false);
    const nodesToReplace = [];

    let node;
    while (node = walk.nextNode()) {
      // Don't highlight tags inside inputs, or interactive controls
      if (node.parentElement.closest('.section-icon-wrapper')) continue;
      
      const text = node.nodeValue;
      const index = text.toLowerCase().indexOf(query);
      if (index >= 0) {
        nodesToReplace.push(node);
      }
    }

    nodesToReplace.forEach(node => {
      const parent = node.parentNode;
      if (!parent) return;

      const text = node.nodeValue;
      const fragment = document.createDocumentFragment();
      let lastIndex = 0;

      // Find all matches in this text node
      let index = text.toLowerCase().indexOf(query);
      while (index >= 0) {
        // Add preceding text
        if (index > lastIndex) {
          fragment.appendChild(document.createTextNode(text.substring(lastIndex, index)));
        }

        // Add highlighted text node
        const mark = document.createElement('mark');
        mark.className = 'highlight';
        mark.appendChild(document.createTextNode(text.substring(index, index + query.length)));
        fragment.appendChild(mark);

        lastIndex = index + query.length;
        index = text.toLowerCase().indexOf(query, lastIndex);
      }

      // Add remaining text
      if (lastIndex < text.length) {
        fragment.appendChild(document.createTextNode(text.substring(lastIndex)));
      }

      parent.replaceChild(fragment, node);
    });
  }

  function removeHighlights(element) {
    const highlights = element.querySelectorAll('mark.highlight');
    highlights.forEach(highlight => {
      const parent = highlight.parentNode;
      if (parent) {
        const textNode = document.createTextNode(highlight.textContent);
        parent.replaceChild(textNode, highlight);
        parent.normalize(); // Merges adjacent text nodes
      }
    });
  }

  function escapeHtml(str) {
    return str.replace(/&/g, '&amp;')
              .replace(/</g, '&lt;')
              .replace(/>/g, '&gt;')
              .replace(/"/g, '&quot;')
              .replace(/'/g, '&#039;');
  }
}

/* ==========================================================================
   6. PDF Download & Print Handler
   ========================================================================== */
function initPrint() {
  const downloadBtn = document.getElementById('download-policy');
  if (downloadBtn) {
    downloadBtn.addEventListener('click', () => {
      window.print();
    });
  }
}

/* ==========================================================================
   7. Interactive Equalizer Console (Landing Page)
   ========================================================================== */
function initEqualizer() {
  const bassKnob = document.getElementById('bass-knob');
  const spatialKnob = document.getElementById('spatial-knob');
  const bassValue = document.getElementById('bass-value');
  const spatialValue = document.getElementById('spatial-value');
  const sliders = document.querySelectorAll('.eq-slider');

  // Guard for pages that don't have the EQ elements (e.g., privacy page)
  if (!bassKnob || !spatialKnob) return;

  // Sliders Logic
  sliders.forEach(slider => {
    const fill = slider.nextElementSibling;
    
    const updateSliderFill = () => {
      const min = parseInt(slider.min) || -12;
      const max = parseInt(slider.max) || 12;
      const val = parseInt(slider.value);
      const percent = ((val - min) / (max - min)) * 100;
      fill.style.height = `${percent}%`;
    };

    slider.addEventListener('input', updateSliderFill);
    updateSliderFill(); // Initial draw
  });

  // Knobs Setup
  setupKnob(bassKnob, bassValue, 70); // Starts at 70%
  setupKnob(spatialKnob, spatialValue, 45); // Starts at 45%

  function setupKnob(knob, valuer, initialPercent) {
    const pointer = knob.querySelector('.knob-pointer');
    
    // Set initial position
    // Range is 0% to 100%, mapped to -135deg to +135deg rotation
    const initialAngle = ((initialPercent / 100) * 270) - 135;
    pointer.style.transform = `translateX(-50%) rotate(${initialAngle}deg)`;
    valuer.textContent = `${initialPercent}%`;
    knob.setAttribute('aria-valuenow', initialPercent);

    let isDragging = false;

    const updateKnobAngle = (clientX, clientY) => {
      const rect = knob.getBoundingClientRect();
      const centerX = rect.left + rect.width / 2;
      const centerY = rect.top + rect.height / 2;

      // Calculate angle relative to knob center
      const dx = clientX - centerX;
      const dy = clientY - centerY;
      let angleRad = Math.atan2(dy, dx);
      let angleDeg = angleRad * (180 / Math.PI);

      // Shift coordinate system so bottom center (facing down) is our midpoint
      // atan2 is 0 on right, 90 down, 180 left, -90 up.
      // We want bottom (90deg) to be the 180deg threshold and center-top (-90deg) to be the limit gap.
      let adjustedAngle = angleDeg + 90;
      if (adjustedAngle > 180) adjustedAngle -= 360;
      if (adjustedAngle < -180) adjustedAngle += 360;

      // Clamp to -135deg to 135deg (leaving a 90deg dead zone at the bottom-middle)
      const clampedAngle = Math.max(-135, Math.min(135, adjustedAngle));

      // Map back to 0 - 100%
      const percent = Math.round(((clampedAngle + 135) / 270) * 100);

      // Rotate pointer & text update
      pointer.style.transform = `translateX(-50%) rotate(${clampedAngle}deg)`;
      valuer.textContent = `${percent}%`;
      knob.setAttribute('aria-valuenow', percent);
    };

    // Mouse Listeners
    knob.addEventListener('mousedown', (e) => {
      isDragging = true;
      document.body.style.cursor = 'grabbing';
      updateKnobAngle(e.clientX, e.clientY);

      const onMouseMove = (moveEvent) => {
        if (isDragging) updateKnobAngle(moveEvent.clientX, moveEvent.clientY);
      };

      const onMouseUp = () => {
        isDragging = false;
        document.body.style.cursor = 'default';
        document.removeEventListener('mousemove', onMouseMove);
        document.removeEventListener('mouseup', onMouseUp);
      };

      document.addEventListener('mousemove', onMouseMove);
      document.addEventListener('mouseup', onMouseUp);
      e.preventDefault();
    });

    // Touch Listeners (Mobile compatibility)
    knob.addEventListener('touchstart', (e) => {
      isDragging = true;
      updateKnobAngle(e.touches[0].clientX, e.touches[0].clientY);

      const onTouchMove = (moveEvent) => {
        if (isDragging) {
          updateKnobAngle(moveEvent.touches[0].clientX, moveEvent.touches[0].clientY);
          moveEvent.preventDefault(); // Stop mobile elastic scroll
        }
      };

      const onTouchEnd = () => {
        isDragging = false;
        document.removeEventListener('touchmove', onTouchMove);
        document.removeEventListener('touchend', onTouchEnd);
      };

      document.addEventListener('touchmove', onTouchMove, { passive: false });
      document.addEventListener('touchend', onTouchEnd);
    });

    // Accessibility Keyboard Controls (Arrow keys)
    knob.addEventListener('keydown', (e) => {
      let currentVal = parseInt(knob.getAttribute('aria-valuenow')) || 0;
      let newVal = currentVal;

      if (e.key === 'ArrowUp' || e.key === 'ArrowRight') {
        newVal = Math.min(100, currentVal + 5);
      } else if (e.key === 'ArrowDown' || e.key === 'ArrowLeft') {
        newVal = Math.max(0, currentVal - 5);
      } else {
        return; // ignore other keys
      }

      e.preventDefault();
      
      const newAngle = ((newVal / 100) * 270) - 135;
      pointer.style.transform = `translateX(-50%) rotate(${newAngle}deg)`;
      valuer.textContent = `${newVal}%`;
      knob.setAttribute('aria-valuenow', newVal);
    });
  }
}
